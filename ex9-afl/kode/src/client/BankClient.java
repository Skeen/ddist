package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import interfaces.Bank;
import interfaces.Account;

import java.lang.reflect.Method;

public class BankClient
{
    public static void main(String args[]) 
    {
        if (System.getSecurityManager() == null) 
        {
            System.setSecurityManager(new SecurityManager());
        }
        try 
        {
            String name = "BankServer";
            Registry registry = LocateRegistry.getRegistry(args[0], Integer.parseInt(args[1]));
            Bank bank = (Bank) registry.lookup(name);

            Account a = bank.getAccount("Jimmy");
            a.deposit(1000);

            System.out.println("OWNER: " + a.getName());
            System.out.println("BALANCE: " + a.getBalance());
        }
        catch (Exception e) 
        {
            System.err.println("BankClient exception:");
            e.printStackTrace();
        }
    }    
}
