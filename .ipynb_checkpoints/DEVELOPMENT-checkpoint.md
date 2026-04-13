# CoCo API Development Guide

This guide explains how to run, modify, and extend the CoCo (Conceptual Complexity) API.

## Table of Contents

- [Quick Start](#quick-start)
- [Architecture Overview](#architecture-overview)
- [Running the API](#running-the-api)
- [Development Workflow](#development-workflow)
- [Adding New Graph Properties](#adding-new-graph-properties)
- [Troubleshooting](#troubleshooting)
- [Useful Commands](#useful-commands)

---

## Quick Start

### First Time Setup

1. **Download the data** (38GB - only needed once):
   ```bash
   export DATA="/home/benjamin/work/github_repositories/cocospa/data"
   ./download_data.sh
   ```

2. **Start the services**:
   ```bash
   ./restart_cocospa.sh
   ```

3. **Test the API**:
   ```bash
   curl -X POST --header 'Content-Type: application/json' \
     -d '{"text": "Some towns on the Eyre Highway in Western Australia."}' \
     http://localhost:8080/complexity | python3 -m json.tool
   ```

### After Reboot

Simply run:
```bash
./restart_cocospa.sh
```

---

## Architecture Overview

### Services

The application consists of three Docker containers:

1. **cocospa** (port 8080) - Main Spring Boot API
2. **dbspotlight** (internal) - DBpedia Spotlight entity linking service
3. **redis** (internal) - Cache for spreading activation computations

### Data Requirements

- **DBpedia Neo4j graph** (~15GB) - Knowledge graph for spreading activation
- **DBpedia HDT file** (~2.3GB) - Compressed DBpedia data
- **DBpedia Spotlight model** (~5GB) - Entity linking model
- **Redis dump** (optional, ~15GB) - Pre-computed activation cache

### Source Code Structure

```
src/main/java/dws/uni/mannheim/semantic_complexity/
├── TextComplexityAssesmentController.java      # REST API endpoints
├── TextComplexityAssesment.java                # Core complexity computation
├── TextComplexityAssesmentRequestObject.java   # API request DTO
├── TextComplexityAssesmentResponseObject.java  # API response DTO
├── spreading_activation/
│   ├── SAComplexityModes.java                  # Spreading activation algorithm
│   └── Mode.java                               # Algorithm parameters
└── ...
```

---

## Running the API

### Starting Services

```bash
# Set the data directory
export DATA="/home/benjamin/work/github_repositories/cocospa/data"

# Start all services
docker-compose up -d

# Check status
docker-compose ps
```

### Stopping Services

```bash
docker-compose down
```

### Viewing Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f cocospa
docker-compose logs -f dbspotlight
docker-compose logs -f redis
```

### Accessing the API

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Complexity Endpoint**: http://localhost:8080/complexity
- **Compare Endpoint**: http://localhost:8080/compare

---

## Development Workflow

### How Code Changes Work

The `docker-compose.yaml` mounts your local `src/` directory into the container:

```yaml
volumes:
  - ./src:/home/cocospa/cocospa/src
```

This means:
- ✅ Changes on your host are immediately visible in the container
- ✅ No need to rebuild Docker images
- ✅ Just restart to recompile

### Making Changes

1. **Edit Java files** in `src/`:
   ```bash
   vim src/main/java/dws/uni/mannheim/semantic_complexity/TextComplexityAssesmentResponseObject.java
   ```

2. **Restart the cocospa service**:
   ```bash
   docker-compose restart cocospa
   ```

3. **Wait for compilation** (~15 seconds):
   ```bash
   # Watch the logs
   docker-compose logs -f cocospa

   # Look for:
   # "INFO  Compiling XX source files..."
   # "INFO  Started Application in X.XXX seconds"
   ```

4. **Test your changes**:
   ```bash
   curl -X POST --header 'Content-Type: application/json' \
     -d '{"text": "Test text."}' \
     http://localhost:8080/complexity | python3 -m json.tool
   ```

### Development Cycle Time

- **Code edit**: Instant
- **Container restart**: 2 seconds
- **Maven recompile**: 10-15 seconds
- **Spring Boot startup**: 5 seconds
- **Total**: ~20 seconds from edit to test

---

## Adding New Graph Properties

### Example: Adding Node Count

This example shows how the `activatedNodeCount` property was added. Use this as a template for adding other properties.

#### 1. Add field to Response Object

**File**: `TextComplexityAssesmentResponseObject.java`

```java
@JsonProperty("activatedNodeCount")
@JsonInclude(JsonInclude.Include.ALWAYS)
private Integer activatedNodeCount = 0;

@JsonProperty("activatedNodeCount")
public Integer getActivatedNodeCount() {
    return activatedNodeCount;
}

@JsonProperty("activatedNodeCount")
public void setActivatedNodeCount(Integer activatedNodeCount) {
    this.activatedNodeCount = activatedNodeCount;
}
```

Update `@JsonPropertyOrder`:
```java
@JsonPropertyOrder({ "complexityScore", "activatedNodeCount" })
```

#### 2. Track the Data in Algorithm

**File**: `SAComplexityModes.java`

In `aggreagateSpreadingActivationsOncePerEntityJedis()`:

```java
// Add tracking at the beginning
Set<Long> allActivatedNodes = new HashSet<>();

// Track nodes when retrieved
if (jedis.exists(seed.getId() + "->" + mention2.getId())) {
    allActivatedNodes.add(mention2.getId());
    // ... rest of code
}

// Return the count
return allActivatedNodes.size();
```

Update method signature:
```java
private int aggreagateSpreadingActivationsOncePerEntityJedis(...)
```

#### 3. Capture in Assessment Service

**File**: `TextComplexityAssesment.java`

Create a result wrapper:
```java
public static class ComplexityResult {
    public double complexityScore;
    public int activatedNodeCount;

    public ComplexityResult(double complexityScore, int activatedNodeCount) {
        this.complexityScore = complexityScore;
        this.activatedNodeCount = activatedNodeCount;
    }
}
```

Update method to return and capture:
```java
int activatedNodeCount = samodes.computeComplexityWithSpreadingActivationOncePerEntity(...);
return new ComplexityResult(1 / simplicityValue, activatedNodeCount);
```

#### 4. Update Controller

**File**: `TextComplexityAssesmentController.java`

```java
TextComplexityAssesment.ComplexityResult result = TextComplexityAssesment.assess(...);
response.setComplexityScore(result.complexityScore);
response.setActivatedNodeCount(result.activatedNodeCount);
```

#### 5. Restart and Test

```bash
docker-compose restart cocospa
sleep 20
curl -X POST --header 'Content-Type: application/json' \
  -d '{"text": "Test"}' \
  http://localhost:8080/complexity | python3 -m json.tool
```

### Other Properties You Can Add

Based on the spreading activation algorithm, you can extract:

- **Graph metrics**:
  - `totalMentionsCount` - Number of entities detected
  - `graphTraversalDepth` - How deep the activation spread
  - `relationshipsTraversed` - Number of edges in the graph used
  - `firedNodesCount` - Nodes that exceeded firing threshold
  - `burnedNodesCount` - Nodes that fired and won't fire again

- **Activation metrics**:
  - `avgActivationAtEncounter` - Average activation when entities first mentioned
  - `avgActivationAtEOS` - Average activation at end of sentence
  - `avgActivationAtEOP` - Average activation at end of paragraph
  - `avgActivationAtEODoc` - Average activation at end of document

- **Text metrics**:
  - `sentenceCount` - Number of sentences
  - `paragraphCount` - Number of paragraphs
  - `tokenCount` - Number of tokens

All these values are already computed internally in `SAComplexityModes.java` - you just need to expose them through the return chain.

---

## Troubleshooting

### Service Won't Start

```bash
# Check status
docker-compose ps

# View logs
docker-compose logs cocospa

# Common issues:
# 1. Permission denied on Neo4j database
sudo chmod -R 777 data/dbpediaNeo4j/

# 2. Port 8080 already in use
sudo lsof -i :8080
# Kill the process or change the port in docker-compose.yaml
```

### Compilation Errors

```bash
# View compilation errors
docker-compose logs cocospa | grep ERROR

# Common issues:
# 1. Syntax errors - fix the Java code
# 2. Missing dependencies - check pom.xml
# 3. Method not found - verify method signatures match
```

### API Returns Empty Response

```bash
# Check if the field is null (excluded by @JsonInclude)
# Add @JsonInclude(JsonInclude.Include.ALWAYS) to the field

# Or set a default value
private Integer activatedNodeCount = 0;
```

### Container Network Issues

```bash
# Recreate network
docker-compose down
docker network prune
docker-compose up -d
```

### Data Not Loading

```bash
# Verify data directory
ls -la data/
# Should see: dbpediaNeo4j/, en/, redis/, DBpedia2014Selected.hdt

# If missing, download again
export DATA="/path/to/data"
./download_data.sh
```

---

## Useful Commands

### Service Management

```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# Restart specific service
docker-compose restart cocospa

# View status
docker-compose ps

# View logs
docker-compose logs -f cocospa

# Execute command in container
docker exec -it cocospa_cocospa_1 bash
```

### Development

```bash
# Restart after code changes
docker-compose restart cocospa && sleep 20

# Watch logs during restart
docker-compose logs -f cocospa

# Test API
curl -X POST --header 'Content-Type: application/json' \
  -d '{"text": "Test"}' \
  http://localhost:8080/complexity

# Check compiled classes
docker exec cocospa_cocospa_1 ls -la /home/cocospa/cocospa/target/classes/dws/uni/mannheim/semantic_complexity/
```

### Data Management

```bash
# Check data size
du -sh data/*

# Backup Redis cache
docker exec cocospa_redis_1 redis-cli SAVE
docker cp cocospa_redis_1:/data/dump.rdb ./backup/

# Clear Redis cache
docker exec cocospa_redis_1 redis-cli FLUSHALL
```

### API Testing

```bash
# Basic complexity test
curl -X POST --header 'Content-Type: application/json' \
  -d '{"text": "Some towns on the Eyre Highway in Western Australia."}' \
  http://localhost:8080/complexity | python3 -m json.tool

# With custom parameters
curl -X POST --header 'Content-Type: application/json' \
  -d '{
    "text": "Test text",
    "graphDecay": 0.25,
    "firingThreshold": 0.01,
    "linkerThreshold": 0.35
  }' \
  http://localhost:8080/complexity | python3 -m json.tool

# Compare two texts
curl -X POST --header 'Content-Type: application/json' \
  -d '{
    "text1": "Simple text.",
    "text2": "Complex text with many entities."
  }' \
  http://localhost:8080/compare | python3 -m json.tool
```

---

## API Reference

### Endpoints

#### POST /complexity

Computes the complexity score for a single text.

**Request Body**:
```json
{
  "text": "Your text here",
  "firingThreshold": 0.01,
  "graphDecay": 0.25,
  "linkerThreshold": 0.35,
  "paragraphDecay": 0.5,
  "phiTo1": true,
  "sentenceDecay": 0.7,
  "tokenDecay": 0.85,
  "useExclusivity": true,
  "usePopularity": true
}
```

**Response**:
```json
{
  "complexityScore": 1.5825065559592115,
  "activatedNodeCount": 2
}
```

#### POST /compare

Compares the complexity of two texts.

**Request Body**:
```json
{
  "text1": "First text",
  "text2": "Second text",
  "graphDecay": 0.25,
  ...
}
```

**Response**:
```json
{
  "text1ComplexityScore": 1.2,
  "text2ComplexityScore": 2.5,
  "comparison": "text2 is more complex than text1"
}
```

### Parameters

- **text** (required): The text to analyze
- **firingThreshold**: Minimum activation for a node to fire (default: 0.01)
- **graphDecay**: Decay factor for spreading activation (default: 0.25)
- **linkerThreshold**: Confidence threshold for entity linking (default: 0.35)
- **tokenDecay**: Decay based on token distance (default: 0.85)
- **sentenceDecay**: Decay between sentences (default: 0.7)
- **paragraphDecay**: Decay between paragraphs (default: 0.5)
- **phiTo1**: Phi function sensitivity (default: true)
- **useExclusivity**: Use relationship exclusivity (default: true)
- **usePopularity**: Use node popularity (default: true)

---

## Performance Notes

### First Request
- **Time**: 5-30 seconds
- **Why**: Spreading activation must be computed and cached
- **Cache**: Stored in Redis for subsequent requests

### Cached Requests
- **Time**: 1-3 seconds
- **Why**: Activations retrieved from Redis cache

### Cache Invalidation
- Redis cache persists across restarts
- Clear with: `docker exec cocospa_redis_1 redis-cli FLUSHALL`

---

## Additional Resources

- **Original Paper**: [ACL 2019 - Assessing the Complexity of Texts](https://www.aclweb.org/anthology/P19-1377.pdf)
- **GitHub Repository**: https://github.com/ioanahulpus/cocospa
- **DBpedia Spotlight**: https://www.dbpedia-spotlight.org/
- **Swagger UI**: http://localhost:8080/swagger-ui.html (when running)

---

## Contributing

When adding new features:

1. **Document your changes** in this file
2. **Add tests** if possible
3. **Update the Response DTO** with new fields
4. **Keep backward compatibility** by making new fields optional
5. **Use meaningful names** for new properties
6. **Add validation** where appropriate

---

## License

See the main repository for license information.
