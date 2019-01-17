package model;

public class AllocationImpl<T,T2> implements Allocation<T,T2> {
	private final T receiver;
	private final T2 resource;
	
	public AllocationImpl(T pl, T2 role) {
		this.receiver = pl;
		this.resource = role;
	}
	
	public int hashCode()
	{
		int res = receiver.hashCode()+resource.hashCode();
		return res;
	}
	
	public boolean equals(Object o)
	{
		AllocationImpl<T,T2> a = (AllocationImpl<T,T2>)o;
		return a.receiver.equals(receiver) && a.resource.equals(resource);
	}
	
	public String toString()
	{
		return receiver+":"+resource;
	}

	public static<T,T2> AllocationImpl<T, T2> newInstance(T usergroup, T2 resource) {
		return new AllocationImpl<T,T2>(usergroup, resource);
	}

	public T2 getResource()
	{
		return resource;
	}

	public T getUser() {
		return receiver;
	}
}
