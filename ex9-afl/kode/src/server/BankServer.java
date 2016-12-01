package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import interfaces.Bank;

public class BankServer 
{
    public static void main(String[] args) 
    {
        if (System.getSecurityManager() == null) 
        {
            System.setSecurityManager(new SecurityManager());
        }
        String name = "BankServer";
        try 
        {
            Bank server = new BankImpl();
            Bank stub = (Bank) UnicastRemoteObject.exportObject(server, 0);
            Registry registry = LocateRegistry.getRegistry(Integer.parseInt(args[0]));
            registry.rebind(name, stub);
            System.out.println(name + " bound");
        }
        catch (Exception e) 
        {
            System.err.println(name + " exception:");
            e.printStackTrace();
        }
    }
}
