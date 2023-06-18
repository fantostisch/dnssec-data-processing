# DNSSEC Data Processing

## Running

```bash
./gradlew run --args="anonymize /path/to/log"
./gradlew run --args="analyze /path/to/log.anon"
# Or anonymize and analzye
./gradlew run --args="aa /path/to/log"
# Analyzing zone files
./gradlew run --args="zone /path/to/zonefiles"
```

## Testing

```bash
./gradlew test
```

## Plotting

See [plots/README.md](plots/README.md)
