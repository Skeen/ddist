package replicated_calculator;
import java.math.BigInteger;

/**
 * An event describing the event of reading a variable.
 * 
 * @author Jesper Buus Nielsen, Arrhus University, 2011
 *
 */

public class ClientEventRead extends ClientEvent {

	public final String var;
	private BigInteger val; 

	/**
	 * 
	 * @param var The name of the variable to read. 
	 */
	public ClientEventRead(String clientName, long eventID, String var) {
		super(clientName,eventID);
		this.var = var;
		this.val = null;
	}
	
	/**
	 * 
	 * @param val Used to record the read value of the variable.
	 */
	public void setVal(BigInteger val) {
		if (this.val == null) {
			this.val = val;
		}
	}

	/**
	 * 
	 * @param val Used to retrieve the read value of the variable.
	 */
	public BigInteger getVal() {
		return val;
	}
	
	public void accept(ClientEventVisitor visitor) {
		visitor.visit(this);
	}
	
}
