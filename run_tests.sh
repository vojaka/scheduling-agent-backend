#!/bin/bash
set -e

# Define Maven version and directories
MAVEN_VERSION="3.9.6"
MAVEN_DIR="/Users/kimsmirnov/.gemini/antigravity/scratch/maven"
MAVEN_TAR="apache-maven-${MAVEN_VERSION}-bin.tar.gz"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/${MAVEN_TAR}"

# Create maven directory if not exists
mkdir -p "$MAVEN_DIR"

if [ ! -d "$MAVEN_DIR/apache-maven-${MAVEN_VERSION}" ]; then
  echo "Downloading Maven ${MAVEN_VERSION}..."
  curl -sSL "$MAVEN_URL" -o "$MAVEN_DIR/$MAVEN_TAR"
  echo "Extracting Maven..."
  tar -xzf "$MAVEN_DIR/$MAVEN_TAR" -C "$MAVEN_DIR"
  rm "$MAVEN_DIR/$MAVEN_TAR"
fi

# Run tests
echo "Running Maven tests..."
"$MAVEN_DIR/apache-maven-${MAVEN_VERSION}/bin/mvn" test -Dtest=DeterministicValidatorTest,ScheduleControllerTest,EnrichShiftsTest,OrchestrationServiceFallbackTest
