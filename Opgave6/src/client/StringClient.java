package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import interfaces.StringModifier;

public class StringClient
{
    public static void main(String args[]) 
    {
        if (System.getSecurityManager() == null) 
        {
            System.setSecurityManager(new SecurityManager());
        }
        try 
        {
            String name = "StringServer";
            Registry registry = LocateRegistry.getRegistry(args[0], Integer.parseInt(args[1]));
            StringModifier modifier = (StringModifier) registry.lookup(name);
            String streng = "den magisk test streng";
            String modified_streng = modifier.modify(streng);
            System.out.println(streng);
            System.out.println("modified to");
            System.out.println(modified_streng);
        }
        catch (Exception e) 
        {
            System.err.println("StringClient exception:");
            e.printStackTrace();
        }
    }    
}
