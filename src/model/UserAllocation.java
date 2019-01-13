package model;

public class UserAllocation implements Allocation<User> {
	
	private final AllocationImpl<User> all;

	private UserAllocation(User u0, Resource resource) {
		all = AllocationImpl.newInstance(u0, resource);
	}

	public static UserAllocation newInstance(User u0, Resource resource) {
		return new UserAllocation(u0, resource);
	}
	
	public String toString()
	{
		return all.toString();
	}
	
	public User getUser()
	{
		return all.getUser();
	}
	
	public Resource getResource()
	{
		return all.getResource();
	}
	
	public int hashCode()
	{
		return all.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((UserAllocation)o).all.equals(all);
	}
	
	
}
