package replicated_calculator;

/**
 * An event describing the addition of two variables and the storing of the result
 * in a third variable.
 * 
 * @author Jesper Buus Nielsen, Arrhus University, 2011
 *
 */
public class ClientEventAdd extends ClientEventWithThreeVariables {

	public ClientEventAdd(String clientName, long eventID, String left, String right, String res) {
		super(clientName, eventID, left, right, res);
	}
		
	public void accept(ClientEventVisitor visitor) {
		visitor.visit(this);
	}
	
}
