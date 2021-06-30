package ca.ubc.ece.resess.slicer.dynamic.core.exceptions;

public class InvalidCommandsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCommandsException () {
        super("Invalid commands");
    }

    public InvalidCommandsException (Exception e) {
        super("Invalid commands", e);
    }
}
