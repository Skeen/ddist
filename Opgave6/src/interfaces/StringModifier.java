package interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface StringModifier extends Remote 
{
	public String modify(String message)
        throws RemoteException;
}

