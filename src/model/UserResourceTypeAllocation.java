package model;

import solver.UserResourceInstanceAllocation;

public class UserResourceTypeAllocation implements Allocation<User, ResourceType> {
	
	private final AllocationImpl<User, ResourceType> all;

	private UserResourceTypeAllocation(User u0, ResourceType resource) {
		all = AllocationImpl.newInstance(u0, resource);
	}

	public static UserResourceTypeAllocation newInstance(User u0, ResourceType resource) {
		return new UserResourceTypeAllocation(u0, resource);
	}
	
	public String toString()
	{
		return all.toString();
	}
	
	public User getUser()
	{
		return all.getUser();
	}
	
	public ResourceType getResource()
	{
		return all.getResource();
	}
	
	public int hashCode()
	{
		return all.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((UserResourceTypeAllocation)o).all.equals(all);
	}

	public static UserResourceTypeAllocation newInstance(UserResourceInstanceAllocation ua) {
		return newInstance(ua.getUser(), ua.getResource().getResourceType());
	}
	
	
}
