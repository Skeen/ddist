package replicated_calculator;
import java.math.BigInteger;
import java.net.InetSocketAddress;

/**
 * 
 * The client of a replicated state machine with integers and their
 * operations. This interface supports connecting to a server along with 
 * assignment, addition, multiplication and comparison of integers. 
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */
public interface Client 
{	
	/**
	 * 
	 * The method connects the client to some server group. This is done by
	 * contacting any server in the server group. Should only be called if 
	 * the client is not connected to a peer group already.
	 * 
	 * After connecting, the client should see the same variables as the other
	 * clients connected to the same server group. Changes done by the client
	 * should eventually be propagated to the other clients and any updates by
	 * other clients connected to the same server group should eventually be 
	 * propagated to this client.
	 * 
	 * @param addressOfServer The address of the server used for connecting to 
	 * 	      the server group.
	 * 
	 * @param clientName Name of this client.
	 * 
	 * @return Connection was successful?
	 */
	public boolean connect(InetSocketAddress serverAddress, String clientName);

	/**
	 * 
	 * Disconnects the local instance from its peer group. Is allowed to
	 * result in a complete loss of state, but is also allowed to maintain
	 * a local copy of the variables of the peer group.
	 * 
	 */
	public void disconnect();

	/**
	 * var := val.
	 * 
	 * @param var The name of a variable to be assigned
	 * @param val The value to assign to the variable
	 */
	public void assign(String var, BigInteger val);

	/**
	 * res := left + right.
	 * 
	 * @param left The name of a variable 
	 * @param left The name of another variable 
	 * @param res The name of a variable which is to be assigned the value of left plus right 
	 */
	public void add(String left, String right, String res);

	/**
	 * res := left * right.
	 * 
	 * @param left The name of a variable 
	 * @param left The name of another variable 
	 * @param res The name of a variable which is to be assigned the value of left multiplied right 
	 */
	public void mult(String left, String right, String res);

	/**
	 * res := [left < right], where [left < right] = -1 if left is less than right, [left < right] = 0 
	 * if left is equal to right and [left < right] = 1 if left is greater than right. 
	 * 
	 * @param left The name of a variable 
	 * @param left The name of another variable 
	 * @param res The name of a variable which is to be assigned the value of left compared to right 
	 */
	public void compare(String left, String right, String res);
	
	/**
	 * Reads the value of the variable named var.
	 * 
	 * @param var The name of the variable to be read
	 * @param callback A callback for returning the value of var.
	 */
	public void read(String var, Callback<BigInteger> callback);
	
	/**
	 * 
	 * The commands executed at the local instance between a beginAtomic and an endAtomic should
	 * be carried out atomically. The exact semantics is up to the implementer. May only be called
	 * if not already within an atomic region.
	 */
	public void beginAtomic();

	/**
	 * @see beginAtomic
	 */
 	public void endAtomic();
	
}
