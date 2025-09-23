TAGS=($(git tag --sort=creatordate | awk '/^v[0-9]+\.[0-9]+\.[0-9]+-bazel$/'))

START="v1.3.14-bazel"
END="v1.6.3-bazel"
COLLECTED=""

# Find the tag range
START_INDEX=-1
END_INDEX=-1
for i in "${!TAGS[@]}"; do
  [[ "${TAGS[$i]}" == "$START" ]] && START_INDEX=$i
  [[ "${TAGS[$i]}" == "$END" ]] && END_INDEX=$i
done

# Collect logs in reverse order (newest to oldest)
for ((i=END_INDEX; i>START_INDEX; i--)); do
  FROM=${TAGS[i-1]}
  TO=${TAGS[i]}
  LOG=$(git log "$FROM".."$TO" --pretty=format:"- %s (%an)")
  if [[ -n "$LOG" ]]; then
    COLLECTED+="## $TO"$'\n'"$LOG"$'\n\n'
  fi
done

# Extract header (first 4 lines) and the rest separately
head -n 5 CHANGELOG.md > CHANGELOG.tmp
echo "" >> CHANGELOG.tmp
echo "$COLLECTED" >> CHANGELOG.tmp
tail -n +5 CHANGELOG.md >> CHANGELOG.tmp

# Replace original file
mv CHANGELOG.tmp CHANGELOG.md
