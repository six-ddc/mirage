PREFIX = /usr/local

all: build

build:
	mvn clean -DskipTests=true package

install: mirage-cli/target/mirage-cli-*.jar
	install -v -p -b mirage-cli/target/mirage-cli-*.jar ${PREFIX}/bin/mirage

#mirage-cli/target/mirage-cli-*.jar: build

uninstall:
	rm -rf ${PREFIX}/bin/mirage

clean:
	mvn clean

.PHONY: all build install uninstall clean
