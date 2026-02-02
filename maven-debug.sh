#!/usr/bin/env bash

set -e -u

echo "🔍 Checking for missing dependency versions..."
# Grep for dependencies without <version> inside pom.xml files
  awk '/<dependency>/ {file=FILENAME} /<version>/ {v=1} /<\/dependency>/ {if (!v) print file ": missing version"} {v=0}' | \
  sort -u

echo -e "\n🔍 Validating Maven parent POM hierarchy..."
# Run effective POM to see if any parent fails to resolve
mvn help:effective-pom > /dev/null || {
  echo "❌ Maven failed to build effective POM. Check parent POM availability."
  exit 1
}

echo -e "\n✅ Maven parent POMs resolve correctly."

echo -e "\n📜 Displaying Maven parent chain..."
# Print the full parent hierarchy (optional)
mvn help:effective-pom -Doutput=effective-pom.xml
xmllint --xpath "//project/parent" effective-pom.xml 2>/dev/null || echo "Could not parse parent hierarchy."

echo -e "\n✅ Script complete."
