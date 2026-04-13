#!/bin/bash

# CoCo Restart Script
# This script restarts the CoCo (Conceptual Complexity) API services
# Run this script after reboot or when services need to be restarted

set -e  # Exit on error

echo "=========================================="
echo "CoCo API Restart Script"
echo "=========================================="
echo ""

# Set the data directory path
export DATA="/home/benjamin/work/github_repositories/cocospa/data"
echo "✓ DATA directory set to: $DATA"
echo ""

# Navigate to the cocospa directory
cd /home/benjamin/work/github_repositories/cocospa
echo "✓ Changed to cocospa directory"
echo ""

# Stop any running containers
echo "Stopping any existing containers..."
docker compose down
echo "✓ Containers stopped"
echo ""

# Fix permissions for Neo4j database (required for write access)
echo "Fixing Neo4j database permissions..."
sudo chmod -R 777 data/dbpediaNeo4j/
echo "✓ Permissions fixed"
echo ""

# Start all services
echo "Starting services (dbspotlight, redis, cocospa)..."
DATA="$DATA" docker compose up -d
echo "✓ Services started"
echo ""

# Wait for services to initialize
echo "Waiting 20 seconds for services to initialize..."
sleep 20
echo "✓ Initialization complete"
echo ""

# Check service status
echo "Service Status:"
echo "----------------------------------------"
docker compose ps
echo ""

# Check if cocospa is running
if docker compose ps | grep -q "cocospa.*Up"; then
    echo "✓ CoCo API is running"
    echo ""
    echo "API Endpoints:"
    echo "  - Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "  - Complexity:  http://localhost:8080/complexity"
    echo "  - Compare:     http://localhost:8080/compare"
    echo ""

    # Test the API
    echo "Testing API..."
    sleep 5
    RESPONSE=$(curl -s -X POST --header 'Content-Type: application/json' -d '{"text": "Test text."}' http://localhost:8080/complexity)

    if echo "$RESPONSE" | grep -q "complexityScore"; then
        echo "✓ API is responding correctly!"
        echo ""
        echo "Sample response:"
        echo "$RESPONSE" | python3 -m json.tool
    else
        echo "⚠ API might not be fully initialized yet"
        echo "Response: $RESPONSE"
        echo ""
        echo "Check logs with: docker compose logs cocospa"
    fi
else
    echo "✗ CoCo API failed to start"
    echo ""
    echo "Check logs with:"
    echo "  docker compose logs cocospa"
    echo "  docker compose logs dbspotlight"
    echo "  docker compose logs redis"
    exit 1
fi

echo ""
echo "=========================================="
echo "Restart Complete!"
echo "=========================================="
echo ""
echo "Useful commands:"
echo "  Stop services:     docker compose down"
echo "  View logs:         docker compose logs -f [service]"
echo "  Restart service:   docker compose restart [service]"
echo "  Service status:    docker compose ps"
echo ""
