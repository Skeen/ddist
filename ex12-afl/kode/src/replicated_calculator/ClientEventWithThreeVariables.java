package replicated_calculator;

/**
 * An event describing an event which operates on two variables, left and right, and 
 * then stores the result in the variable res.
 * 
 * @author Jesper Buus Nielsen, Arrhus University, 2011
 *
 */

public abstract class ClientEventWithThreeVariables extends ClientEvent {
	
	public final String left;
	public final String right;
	public final String res;	

	/**
	 * 
	 * @param left  The name of the left operand
	 * @param right The name of the right operand
	 * @param res The name of the result variable 
	 */
	ClientEventWithThreeVariables(String clientName, long eventID, String left, String right, String res) {
		super(clientName,eventID);
		this.left = left;
		this.right = right;
		this.res = res;
	}
	
}
