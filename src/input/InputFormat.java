package input;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import model.Resource;
import model.ResourceProvider;
import model.User;
import model.UserAllocation;
import model.UserGroup;

public class InputFormat {
	private final int minNbUsersPerResource;
	private final int maxNbUsersPerResource;
	private final Map<UserAllocation, Integer> absolutePrefsPerAllocation;
	private final Set<UserGroup> userGroups;
	private final Map<Resource, ResourceProvider> providerPerResource;
	
	private InputFormat(
			Map<UserAllocation, Double> baseValues,
			int numberOfUsersPerResource,
			int maxNbUsersPerResource,
			Set<UserGroup> userGroups,
			Map<Resource, ResourceProvider> providerPerResource
			) {
		this.minNbUsersPerResource = numberOfUsersPerResource;
		this.maxNbUsersPerResource = maxNbUsersPerResource;
		 absolutePrefsPerAllocation = 
					toRelativePreferences(baseValues);
		 this.userGroups = userGroups;
		 this.providerPerResource = providerPerResource;
	}
	
	public static InputFormat newInstance(Map<UserAllocation, Double> baseValues,
			int numberOfUsersPerResource,
			int maxNbUsersPerResource,
			Set<UserGroup> groups, 
			Map<Resource, ResourceProvider> providerPerResource)
	{
		return new InputFormat(
				baseValues,
				numberOfUsersPerResource,
				maxNbUsersPerResource, 
				groups,
				providerPerResource);
	}
	
	public int getMaxNbUsersPerResource() {
		return maxNbUsersPerResource;
	}
	public int getMinNumUsersPerResource() {
		return minNbUsersPerResource;
	}
	public Set<Resource> getResources() {
		return 
				absolutePrefsPerAllocation.keySet()
				.stream()
				.map(x->x.getResource())
				.collect(Collectors.toSet());
	}
	public Map<UserAllocation, Integer> getExhaustiveAllocations() {
		return absolutePrefsPerAllocation;
	}
	
	//not implemented
	public Set<UserAllocation> getHardConstraints() {
		return new HashSet();
	}
	public Set<User> getAllUsers() {
		return absolutePrefsPerAllocation.keySet()
				.stream()
				.map(x->x.getUser())
				.collect(Collectors.toSet());
	}
	
	private static Map<UserAllocation,Integer> toRelativePreferences(
			Map<UserAllocation, Double> 
			baseValues)
	{

		Set<User> requesters = 
				baseValues
				.keySet()
				.stream()
				.map(x->x.getUser())
				.collect(Collectors.toSet());
		
		Set<Resource> resources = 
				baseValues.keySet()
				.stream()
				.map(x->x.getResource())
				.collect(Collectors.toSet());
		
		Map<UserAllocation, Integer> res = new HashMap<>();
		
		for(User s: requesters)
		{
			Set<UserAllocation> positiveValues = baseValues.keySet().stream()
					.filter(x->x.getUser().equals(s) && baseValues.get(x)> 0)
					.collect(Collectors.toSet());
			
			//sort by decreasing order
			Set<UserAllocation> sortedPositiveValues = new TreeSet<>((x, y)-> {
				if(baseValues.get(x).equals(baseValues.get(y)))
					return x.toString().compareTo(y.toString());
				return -Double.compare(baseValues.get(x), 
						baseValues.get(y));});
			
			sortedPositiveValues.addAll(positiveValues);
			
			
			Set<UserAllocation> negativeValues =  baseValues.keySet().stream()
					.filter(x->x.getUser().equals(s) && baseValues.get(x)< 0).collect(Collectors.toSet());
			Set<UserAllocation> sortedNegativeValues = new TreeSet<>(
					(x, y)-> {
						if(baseValues.get(x).equals(baseValues.get(y))) return x.toString().compareTo(y.toString());
						return -Double.compare(baseValues.get(x), 
								baseValues.get(y));});
			sortedNegativeValues.addAll(negativeValues);
			
			Set<Resource> nonAllocatedResources = new HashSet<>();
			nonAllocatedResources.addAll(resources);
			nonAllocatedResources.removeAll(positiveValues.stream().map(x->x.getResource()).collect(Collectors.toList()));
			nonAllocatedResources.removeAll(negativeValues.stream().map(x->x.getResource()).collect(Collectors.toList()));
			
			int currentOrder = 0;
			int currentIndex = 0;
			UserAllocation prev = null;
			for(UserAllocation a:sortedPositiveValues)
			{
				if(prev != null && !baseValues.get(a).equals(baseValues.get(prev)))
					currentOrder = currentIndex;
				prev = a;
				currentIndex++;
				
				res.put(a, currentOrder);
			}
			
			currentOrder = currentIndex;
			for(Resource allocatee:nonAllocatedResources)
			{
				currentIndex++;
				res.put(UserAllocation.newInstance(s, allocatee), currentOrder);
			}
			currentOrder = currentIndex;
			for(UserAllocation a:sortedNegativeValues)
			{
				if(prev != null && !baseValues.get(a).equals(baseValues.get(prev)))
					currentOrder = currentIndex;
				prev = a;
				currentIndex++;
				
				res.put(a, currentOrder);
			}

		}
		return res;
	}
	
	public Set<UserGroup> getUserGroups() {
		return userGroups;
	}

	public Set<UserAllocation> getAllAllocationsFor(User pl) {
		return absolutePrefsPerAllocation.keySet().stream()
				.filter(x->x.getUser().equals(pl))
				.collect(Collectors.toSet());
	}

	public Set<UserAllocation> getAllocationsForResource(Resource resource) 
	{
		return 
				absolutePrefsPerAllocation.keySet().stream()
				.filter(x->x.getResource().equals(resource))
				.collect(Collectors.toSet());
	}

	public ResourceProvider getOwner(Resource r) {
		return providerPerResource.get(r);
	}

	public Set<ResourceProvider> getAllResourceOwners() {
		return providerPerResource.values()
				.stream()
				.collect(Collectors.toSet());
	}

	public Set<Resource> getResourcesFrom(ResourceProvider rp) {
		return providerPerResource.keySet()
				.stream().filter(x->providerPerResource.get(x).equals(rp))
				.collect(Collectors.toSet());
	}
	
	public UserGroup getUserGroupOf(User u)
	{
		Set<UserGroup>groups = userGroups.stream().filter(x->x.getUsers().contains(u))
				.collect(Collectors.toSet());
		assert(groups.size()==1);
		return groups.iterator().next();
	}
}
