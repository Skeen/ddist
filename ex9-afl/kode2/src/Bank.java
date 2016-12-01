public interface Bank
{
    /**
     * Returns an existing Account if one with that name exists. otherwise
     * creates a new Account
     * @param name The name to lookup the account for
     * @return A String describing the account in the name of name.
     */
	public String getAccount(String name);
}

