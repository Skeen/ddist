package replicated_calculator;

/**
 * An interface used for visiting client events.
 * When ClientEvent.accept is called with a visitor, it will
 * do a callback to the visitor. Polymorphism can then be used
 * to handle the sub-classes of ClientEvent.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */

public interface ClientEventVisitor {
	public void visit(ClientEventAdd event);
	public void visit(ClientEventAssign event);
	public void visit(ClientEventBeginAtomic event);
	public void visit(ClientEventCompare event);
	public void visit(ClientEventConnect event);
	public void visit(ClientEventDisconnect event);
	public void visit(ClientEventEndAtomic event);
	public void visit(ClientEventMult event);
	public void visit(ClientEventRead event);
}
