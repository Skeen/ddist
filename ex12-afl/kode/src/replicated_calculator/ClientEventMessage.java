package replicated_calculator;
import java.io.*;

public class ClientEventMessage extends ClientEvent 
{
    private String s;

	public ClientEventMessage(String clientName, long eventID, String s)
    {
        super(clientName, eventID);
        this.s = s;
	}
	
	public void accept(ClientEventVisitor visitor)
    {
        System.out.println("WTF");
    }

    public String getString()
    {
        return s;
    }
}
