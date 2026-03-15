#!/bin/bash
# Initialize Garage: layout, buckets, API key
# Run after `docker compose up -d` and Garage is healthy

set -e

GARAGE="docker compose exec -T garage /garage"

echo "Waiting for Garage to be ready..."
until $GARAGE status 2>/dev/null | grep -q "ID"; do
  sleep 2
done

# Get node ID and assign layout
NODE_ID=$($GARAGE status | grep -oE '[0-9a-f]{16}' | head -1)
echo "Node ID: $NODE_ID"

# Assign capacity (1GB zone, tag=local)
$GARAGE layout assign -z local -c 1G "$NODE_ID" 2>/dev/null || true
$GARAGE layout apply --version 1 2>/dev/null || true

# Create buckets
$GARAGE bucket create gabon-videos 2>/dev/null || echo "Bucket gabon-videos already exists"
$GARAGE bucket create gabon-avatars 2>/dev/null || echo "Bucket gabon-avatars already exists"

# Create API key
KEY_OUTPUT=$($GARAGE key create gabon-app-key 2>/dev/null || $GARAGE key info gabon-app-key)

ACCESS_KEY=$(echo "$KEY_OUTPUT" | grep -oP 'GK[a-zA-Z0-9]+' | head -1)
SECRET_KEY=$(echo "$KEY_OUTPUT" | grep -oP 'Secret key: \K.*' || echo "$KEY_OUTPUT" | grep -A1 "Secret" | tail -1 | tr -d ' ')

# Grant permissions
$GARAGE bucket allow --read --write --owner gabon-videos --key gabon-app-key 2>/dev/null || true
$GARAGE bucket allow --read --write --owner gabon-avatars --key gabon-app-key 2>/dev/null || true

echo ""
echo "=== Garage initialized ==="
echo "S3 Endpoint: http://localhost:3900"
echo "Region: garage"
echo "Access Key: $ACCESS_KEY"
echo "Secret Key: (see 'garage key info gabon-app-key')"
echo ""
echo "Add to .env:"
echo "S3_ENDPOINT=http://localhost:3900"
echo "S3_REGION=garage"
echo "S3_ACCESS_KEY=$ACCESS_KEY"
echo "S3_SECRET_KEY=<secret from above>"
echo "S3_BUCKET_VIDEOS=gabon-videos"
echo "S3_BUCKET_AVATARS=gabon-avatars"
