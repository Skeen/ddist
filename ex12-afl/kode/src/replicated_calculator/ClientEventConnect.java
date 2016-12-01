package replicated_calculator;
import java.net.InetSocketAddress;

/**
 * An event describing a client connecting to a server.
 * 
 * @author Jesper Buus Nielsen, Arrhus University, 2011
 *
 */

public class ClientEventConnect extends ClientEvent {

	final public InetSocketAddress clientAddress; 

	/**
	 * 
	 * @param clientName The name of the client, @see ClientEvent#clientName
	 * @param eventID The identifier of the event, @see ClientEvent#eventID
	 * @param clientAddress The address on which the client open the receiver end of its queue
	 */
	public ClientEventConnect(String clientName, long eventID, InetSocketAddress clientAddress) {
		super(clientName,eventID);
		this.clientAddress = clientAddress;
	}

	public void accept(ClientEventVisitor visitor) {
		visitor.visit(this);
	}
	
}
