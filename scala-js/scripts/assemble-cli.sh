#! /bin/sh

set -e

# Assembles the CLI tools for a given Scala binary version.

if [ $# -lt 1 ]; then
    echo "Usage: $(basename $0) <binary scala version> [noclean|nobuild]" >&2
    exit 1
fi

BINVER=$1
case $BINVER in
    2.10)
        FULLVERS="2.10.2 2.10.3 2.10.4 2.10.5 2.10.6"
        BASEVER="2.10.6"
        ;;
    2.11)
        FULLVERS="2.11.0 2.11.1 2.11.2 2.11.4 2.11.5 2.11.6 2.11.7 2.11.8"
        BASEVER="2.11.8"
        ;;
    *)
        echo "Invalid Scala version $BINVER" >&2
        exit 2
esac

if [ "$2" != "nobuild" ]; then
    # Subshell to generate SBT commands
    (
        if [ "$2" != "noclean" ]; then
            echo "clean"
        fi

        # Assemble cli-tools
        echo "project cli"
        echo "++$BASEVER"
        echo "assembly"

        # Package Scala.js library
        echo "project library"
        echo "++$BASEVER"
        echo "package"

        # Package compiler
        echo "project compiler"
        for i in $FULLVERS; do
            echo "++$i"
            echo "package"
        done
    ) | sbt || exit $?
fi

# Copy stuff to right location
BASE="$(dirname $0)/.."

# Target directories
TRG_BASE="$BASE/cli/pack"
TRG_VER="$TRG_BASE/$BINVER"
TRG_LIB="$TRG_VER/lib"
TRG_BIN="$TRG_VER/bin"

rm -rf $TRG_VER
mkdir -p $TRG_LIB
mkdir -p $TRG_BIN

SCALAJS_VER=$(ls $BASE/cli/target/scala-$BINVER/scalajs-cli-assembly_$BINVER-*.jar | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT|-RC[0-9]+|-M[0-9]+)?')

cp $BASE/cli/target/scala-$BINVER/scalajs-cli-assembly_$BINVER-$SCALAJS_VER.jar $TRG_LIB
cp $BASE/library/target/scala-$BINVER/scalajs-library_$BINVER-$SCALAJS_VER.jar $TRG_LIB

for i in $FULLVERS; do
    cp $BASE/compiler/target/scala-$BINVER/scalajs-compiler_$i-$SCALAJS_VER.jar $TRG_LIB
done

PAT="s/@SCALA_BIN_VER@/$BINVER/; s/@SCALAJS_VER@/$SCALAJS_VER/"
PREF=$BASE/cli/src/main/resources/
for i in $PREF*; do
    out=$TRG_BIN/${i#$PREF}
    # Redirect sed output, since in-place edit doesn't work
    # cross-platform
    sed "$PAT" $i > $out
    # Add executable flag if required
    if [ -x $i ]; then
        chmod +x $out
    fi
done

# Tar and zip the whole thing up
OUT=scalajs_$BINVER-$SCALAJS_VER

tar cfz $TRG_BASE/$OUT.tgz --exclude '*~' -C $TRG_VER lib bin
(
  if [ -f $OUT.zip ]; then rm $OUT.zip; fi
  cd $TRG_VER
  zip -r ../$OUT.zip -r lib bin -x '*~'
)
