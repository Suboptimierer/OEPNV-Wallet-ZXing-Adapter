#!/bin/bash

# Konfiguration
MAIN_CLASS="Main"
TMP_DIR="tmp"
JAR_NAME="zxing-adapter.jar"
LIB_DIR="lib"
DEPENDENCY_JARS="$LIB_DIR/zxing-core-3.5.3.jar:$LIB_DIR/zxing-javase-3.5.3.jar"

# Altes tmp und JAR löschen, neues tmp anlegen
rm -rf "$TMP_DIR" "$JAR_NAME"
mkdir -p "$TMP_DIR"

# Kompilieren des Java-Quellcodes ins tmp-Verzeichnis
javac -cp "$DEPENDENCY_JARS" -d "$TMP_DIR" "src/$MAIN_CLASS.java"
if [ $? -ne 0 ]; then
  echo "Kompilierung fehlgeschlagen"
  exit 1
fi

# Dependencies ins tmp-Verzeichnis entpacken
for jar in $LIB_DIR/*.jar; do
  unzip -oq "$jar" -d "$TMP_DIR"
done

# Erstellen der JAR aus allen Klassen (inkl. Abhängigkeiten)
cd "$TMP_DIR"
jar --create --file "../$JAR_NAME" --main-class="$MAIN_CLASS" .
cd ..

# Erfolgsmeldung
echo "Build erfolgreich: $JAR_NAME"

# Aufräumen: temporäres Verzeichnis löschen
rm -rf "$TMP_DIR"