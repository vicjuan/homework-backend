# Setup Notes

## RocketMQ Broker Configuration

Before starting services, ensure `broker.conf` contains the following line:

```
brokerIP1 = 127.0.0.1
```

Without this, the Broker advertises its Docker-internal IP to the NameServer,
which is unreachable from the host machine, causing all message sends to fail.

## Database Schema

The `notifications` table is created automatically via `init.sql` on first startup.

If the table is missing (e.g. the Docker volume already existed from a previous run),
run the script manually:

```bash
docker exec -i mysql mysql -utaskuser -ptaskpass taskdb < init.sql
```

## Run Tests

```bash
./mvnw test
```

## Example API Calls

```bash
# Create a notification
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -d '{"type":"email","recipient":"user@example.com","subject":"Hello","content":"World"}'

# Get by ID
curl http://localhost:8080/notifications/1

# Get recent notifications
curl http://localhost:8080/notifications/recent

# Update a notification
curl -X PUT http://localhost:8080/notifications/1 \
  -H "Content-Type: application/json" \
  -d '{"subject":"Updated Subject","content":"Updated Content"}'

# Delete a notification
curl -X DELETE http://localhost:8080/notifications/1
```
