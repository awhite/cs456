JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
	EotException.java \
	Packet.java \
	Receiver.java \
	Sender.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
