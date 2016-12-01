package replicated_calculator;
import pointtopointqueue.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.net.InetSocketAddress;

public class debugVisitor implements ClientEventVisitor 
{
    public void visit(ClientEventAdd eventAdd) 
    {
        System.out.println("ClientEventAdd eventAdd: " + eventAdd);
    }

    public void visit(ClientEventAssign eventAssign) 
    {
        System.out.println("ClientEventAssign eventAssign: " + eventAssign);
    }

    public void visit(ClientEventBeginAtomic _) 
    {
        System.out.println("ClientEventBeginAtomic _: " + _);
    }

    public void visit(ClientEventCompare eventCompare) 
    {
        System.out.println("ClientEventCompare eventCompare: " + eventCompare);
    }

    public void visit(ClientEventConnect eventConnect) 
    {
        System.out.println("ClientEventConnect eventConnect: " + eventConnect);
    }

    public void visit(ClientEventDisconnect eventDisconnect) 
    {
        System.out.println("ClientEventDisconnect eventDisconnect: " + eventDisconnect);
    }

    public void visit(ClientEventEndAtomic _) 
    {
        System.out.println("ClientEventEndAtomic _: " + _);
    }

    public void visit(ClientEventMult eventMult) 
    {
        System.out.println("ClientEventMult eventMult: " + eventMult);
    }

    public void visit(ClientEventRead eventRead) 
    {
        System.out.println("ClientEventRead eventRead: " + eventRead);
    }
}

