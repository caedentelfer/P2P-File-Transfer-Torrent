# Compiler
JAVAC = javac
# Java interpreter
JAVA = java
# Flags for compiling
JFLAGS = -d bin -cp bin

# Targets
.PHONY: all clean

all: server client

server: bin/P2PServer.class

client: bin/P2PClient.class

# Compile server
bin/P2PServer.class: src/P2PServer.java src/P2PClientHandler.java | bin
	$(JAVAC) $(JFLAGS) $^

# Compile client
bin/P2PClient.class: src/P2PClient.java | bin
	$(JAVAC) $(JFLAGS) $^

# Create bin directory if it doesn't exist
bin:
	mkdir -p bin

# Clean up
clean:
	@if [ -n "$(wildcard bin/*.class)" ]; then \
        rm bin/*.class; \
        echo "Removed .class files from the bin directory."; \
    fi
	@if [ -n "$(wildcard *.pdf)" ]; then \
        rm *.pdf; \
        echo "Removed .pdf files."; \
    fi
	@if [ -n "$(wildcard *.word)" ]; then \
        rm *.word; \
        echo "Removed .word files."; \
    fi
	@if [ -n "$(wildcard *.txt)" ]; then \
        rm *.txt; \
        echo "Removed .txt files."; \
    fi
	@if [ -n "$(wildcard *.csv)" ]; then \
        rm *.csv; \
        echo "Removed .csv files."; \
    fi
	@if [ -n "$(wildcard *.log)" ]; then \
        rm *.log; \
        echo "Removed .log files."; \
    fi

# Run server
run-server: server
	$(JAVA) -cp bin P2PServer

# Run client
run-client: client
	$(JAVA) -cp bin P2PClient
