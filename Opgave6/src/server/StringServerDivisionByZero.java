package server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import interfaces.StringModifier;

public class StringServerDivisionByZero implements StringModifier 
{
    public StringServerDivisionByZero() 
    {
        super();
    }

    public String modify(String message)
        throws RemoteException
    {
        return "" + (1/0);
    }

    public static void main(String[] args) 
    {
        if (System.getSecurityManager() == null) 
        {
            System.setSecurityManager(new SecurityManager());
        }
        try 
        {
            String name = "StringServer";
            StringModifier server = new StringServerDivisionByZero();
            StringModifier stub = (StringModifier) UnicastRemoteObject.exportObject(server, 0);
            Registry registry = LocateRegistry.getRegistry(Integer.parseInt(args[0]));
            registry.rebind(name, stub);
            System.out.println("StringServerDivisionByZero bound");
        }
        catch (Exception e) 
        {
            System.err.println("StringServerDivisionByZero exception:");
            e.printStackTrace();
        }
    }
}
