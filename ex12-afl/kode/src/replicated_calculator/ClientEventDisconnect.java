package replicated_calculator;

/**
 * An event describing a client disconnecting from a server.
 * 
 * @author Jesper Buus Nielsen, Arrhus University, 2011
 *
 */

public class ClientEventDisconnect extends ClientEvent {
	
	ClientEventDisconnect(String clientName, long eventID) {
		super(clientName,eventID);
	}
	
	public void accept(ClientEventVisitor visitor) {
		visitor.visit(this);
	}
	
}
