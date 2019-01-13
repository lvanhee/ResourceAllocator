package model;

public class Resource {
	
	private final String name;

	private Resource(String name) {
		this.name = name;
	}

	public static Resource newInstance(String name) {
		return new Resource(name);
	}
	
	public int hashCode()
	{
		return name.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((Resource)o).name.equals(name);
	}
	
	public String toString()
	{
		return name;
	}

}
