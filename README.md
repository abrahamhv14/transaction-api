# Transaction Execution API — Spin Backend Challenge

API REST que ejecuta transacciones financieras (CREDIT/DEBIT) contra un
proveedor externo (mockeado), persiste el resultado y expone un endpoint de
consulta con filtros y paginación.

## Stack

- **Java 17 + Spring Boot 3.3**
- **PostgreSQL** (producción) / **H2 in-memory** (tests)
- **Resilience4j** — retry + circuit breaker para el cliente del proveedor
- **WireMock** — mock del proveedor externo en tests de integración
- **JUnit 5 + Mockito + AssertJ**
- **springdoc-openapi** — Swagger UI en `/swagger-ui.html`
- **Docker / docker-compose**

## Cómo levantar el proyecto

### Opción A — Docker Compose (recomendada)

```bash
# 1. En una terminal, levanta la API + Postgres + wiremock, 
# el proyecto esta configurado con las imagenes necesarias basta con ejecutar el siguiente comando:

docker compose up --build

# 2. Para detener el proyecto (contenedores):

docker compose down

```

La API queda disponible en `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Opción B — Local con Maven

```bash
# 1. Levanta los contenedores Postgres y WireMock:

docker compose up postgres wiremock -d

# 2. Levanta la API con Maven, pasando las variables de entorno necesarias:
mvn spring-boot:run \
  -DDB_URL=jdbc:postgresql://localhost:5432/transactions_db \
  -DPROVIDER_BASE_URL=http://localhost:9090
```

### Correr los tests

```bash
mvn test
```

Los tests de integración del cliente del proveedor levantan WireMock
programáticamente (no requieren nada externo corriendo).

## Ejemplo de uso

```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
        "accountId": "acc-123456",
        "type": "CREDIT",
        "amount": 1500.00,
        "currency": "MXN",
        "description": "Transferencia recibida"
      }'

curl "http://localhost:8080/transactions?accountId=acc-123456&status=EXECUTED&page=0&limit=20"
```

## Decisiones de diseño

### Arquitectura en capas

```
controller/   -> HTTP, serialización, códigos de estado
service/      -> Reglas de negocio + orquestación (no sabe de HTTP)
provider/     -> Puerto (interfaz ProviderClient) + adaptador HTTP concreto
repository/   -> Persistencia (Spring Data JPA + Specifications)
domain/       -> Entidad Transaction y enums
dto/          -> Contratos públicos de la API (separados de la entidad)
exception/    -> Excepciones de dominio + manejo centralizado de errores
```

El **servicio nunca depende del cliente HTTP concreto**, solo de la interfaz
`ProviderClient`. Esto permite:
- Testear las reglas de negocio y la orquestación con un stub simple (sin
  levantar HTTP ni Spring context completo).
- Cambiar de proveedor o de protocolo (REST → gRPC, por ejemplo) sin tocar
  el dominio.

### Persistencia: PostgreSQL

Se eligió PostgreSQL porque:
- Las transacciones financieras requieren **consistencia ACID** real (no
  solo eventual), y Postgres la da de forma nativa.
- A "millones de transacciones diarias" (~cientos de escrituras/segundo en
  picos), Postgres escala bien con índices adecuados (`accountId`,
  `status`, `type`, `createdAt` — ver `Transaction`) y, si hiciera falta más
  adelante, particionado por fecha o por `accountId`.
- Es el estándar de facto para este tipo de sistema y coincide con el stack
  mencionado por Spin.

Para tests se usa **H2 in-memory en modo PostgreSQL**, lo que da tests
rápidos y aislados sin sacrificar demasiada fidelidad al dialecto SQL real.

### Reglas de negocio antes del proveedor

`TransactionRulesValidator` aplica monto mínimo, monto máximo (solo DEBIT) y
moneda soportada **antes** de tocar el proveedor externo, evitando llamadas
innecesarias y devolviendo errores 400 descriptivos y rápidos al cliente.
Está separado del `TransactionService` para que sea testeable de forma
aislada y con responsabilidad única.

### Manejo del proveedor externo: desacoplamiento + resiliencia

- **Interfaz `ProviderClient`** como puerto; `HttpProviderClient` es el único
  adaptador que conoce el contrato HTTP (`POST /provider/v1/execute`).
- **Resilience4j `@Retry`**: reintenta automáticamente fallos técnicos
  transitorios (timeouts, 5xx) con backoff exponencial (3 intentos, 200ms
  inicial).
- **Resilience4j `@CircuitBreaker`**: si el proveedor falla de forma
  sostenida, el circuito se abre y las llamadas fallan rápido en lugar de
  saturar el proveedor o acumular latencia — crítico en un sistema de alto
  volumen.
- **Distinción explícita entre rechazo de negocio y fallo técnico**:
  - `ProviderRejectedException` — el proveedor respondió pero rechazó la
    operación (ej. `INSUFFICIENT_FUNDS`). **No se reintenta** ni cuenta como
    fallo del circuit breaker (está en `ignore-exceptions`), porque es un
    resultado de negocio válido, no un error del sistema.
  - `ProviderCommunicationException` — no fue posible comunicarse con el
    proveedor tras agotar reintentos (o circuito abierto). Se traduce a
    `503 Service Unavailable` para el cliente de la API.
- **Timeouts** de conexión/lectura configurados directamente en el
  `RestClient` (1s conexión / 2s lectura por defecto), porque son síncronos
  y complementan al retry/circuit breaker.

### Registro de auditoría completo

Toda transacción se persiste con su estado final (`EXECUTED` o `REJECTED`),
incluso cuando el proveedor la rechaza — así el endpoint de consulta
siempre refleja qué pasó, no solo los éxitos. Cuando falla la comunicación
técnica con el proveedor (`ProviderCommunicationException`), actualmente se
responde `503` sin persistir un registro `FAILED`; esto es una decisión
consciente para no ocultar el error al cliente que hizo el request (sabe
inmediatamente que debe reintentar). Una evolución natural sería persistir
igualmente un registro `FAILED` y ofrecer reconciliación asíncrona
(ej. vía Kafka) para escenarios de alto volumen — se dejó fuera del alcance
del challenge por simplicidad, pero es la primera extensión que haría en
producción.

### Filtros y paginación

`GET /transactions` usa `JpaSpecificationExecutor` con specifications
combinables (`accountId`, `status`, `type`), en vez de escribir un método de
repositorio por cada combinación de filtros. `page`/`limit` se mapean a
`Pageable` de Spring Data (con un límite de 100 por página para evitar
respuestas descontroladamente grandes).

### Por qué no se sobre-ingenierizó

- No se implementó el proveedor externo real, para ello se usa
  WireMock tanto en tests como para pruebas manuales locales.
- No se agregó caché, mensajería (Kafka o RabbitMQ): dado que el
  challenge no lo pide, sin embargo la arquitectura permite añadirlo fácilmente 
  en el futuro (por ejemplo, un `TransactionEventPublisher` que publique eventos.
- 
## Testing

- **`TransactionRulesValidatorTest`** — reglas de negocio en aislamiento
  (monto mínimo/máximo, moneda).
- **`TransactionServiceTest`** — orquestación del servicio con
  `ProviderClient` y `TransactionRepository` mockeados (Mockito).
- **`HttpProviderClientTest`** — cliente HTTP real contra WireMock,
  incluyendo verificación de que los rechazos de negocio NO se reintentan y
  que los fallos técnicos SÍ (3 intentos).
- **`TransactionControllerTest`** — flujo end-to-end vía MockMvc + H2,
  mockeando solo el `ProviderClient` (puerto), no el HTTP.

## Uso de Inteligencia Artificial

Este proyecto fue generado con
asistencia de **Claude (Anthropic) con el IDE Cursor AI** incluyendo: 
Optimizacion de codigo, configuración de Resilience4j,
y redacción de tests unitarios/integración.
Las decisiones de diseño (elección de PostgreSQL, separación de
responsabilidades, manejo de rechazos vs. fallos técnicos, trade-off sobre
persistir o no transacciones `FAILED`) fueron dirigidas y revisadas
explícitamente.
