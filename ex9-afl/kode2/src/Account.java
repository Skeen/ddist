/**
 * Each Account maintains a balance that may be raised due to deposits and
 * lowered due to withdraws
 */
public interface Account 
{
	public String getName();
    public double getBalance();
    public void deposit(String amount);
    public void withdraw(String amount);
}

