package input;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import input.InputBuilder.ParameterTypes;
import main.Main;
import model.ResourceType;
import model.ResourceOwner;
import model.User;
import model.UserResourceTypeAllocation;
import solver.ResourceInstance;
import solver.UserResourceInstanceAllocation;
import model.UserGroup;

public class ProblemInstance {
	
	public enum OwnerDesire{AT_LEAST_ONE_INSTANCE_PER_OWNER, NO_DESIRE};
	
	private final int minNbUsersPerResource;
	private final int maxNbUsersPerResource;
	private final Map<UserResourceInstanceAllocation, Integer> relativePrefPerResourceAllocation;
	private final Set<UserGroup> userGroups;
	private final Function<ResourceType, ResourceOwner> providerPerResource;	
	private final Function<ResourceType, Integer> amountOfInstancesPerResourceType;
	private final OwnerDesire od;
	private final OutputType ot;
		
	private ProblemInstance(
			Map<UserResourceTypeAllocation, Double> baseValues,
			int numberOfUsersPerResource,
			int maxNbUsersPerResource,
			Set<UserGroup> userGroups,
			Function<ResourceType, ResourceOwner> providerPerResource,
			Function<ResourceType, Integer> amountPerResource,
			UserPreferenceMeaning upm,
			OutputType ot,
			OwnerDesire od
			) {
		this.od = od;
		this.ot = ot;
		this.amountOfInstancesPerResourceType = amountPerResource;
		this.minNbUsersPerResource = numberOfUsersPerResource;
		this.maxNbUsersPerResource = maxNbUsersPerResource;
		 relativePrefPerResourceAllocation = 
					toRelativePreferences(baseValues,upm, amountPerResource);
		 this.userGroups = userGroups;
		 this.providerPerResource = providerPerResource;
		 
		 check();

	}
	
	private void check() {
		if(getNbAllocableSlots() < getAllUsers().size())
			throw new Error("Not enough slots for satisfying all users. Available slots:"+getNbAllocableSlots()+
					"Nb Users:"+getAllUsers());
	}
	
	public int getAmountOf(ResourceType r)
	{
		return amountOfInstancesPerResourceType.apply(r);
	}

	private int getNbAllocableSlots() {
		return getResourceTypes()
				.stream()
				.map(x->getAmountOf(x))
				.reduce(0, (x,y)->x+y)
				*
				getMaxNbUsersPerResource();
	}

	public static ProblemInstance newInstance
	(
			Map<UserResourceTypeAllocation, Double> baseValues,
			int numberOfUsersPerResource,
			int maxNbUsersPerResource,
			Set<UserGroup> groups, 
			Function<ResourceType, ResourceOwner> providerPerResource,
			Function<ResourceType, Integer> amountPerResource,
			UserPreferenceMeaning upm, OutputType ot, OwnerDesire od)
	{
		return new ProblemInstance(
				baseValues,
				numberOfUsersPerResource,
				maxNbUsersPerResource, 
				groups,
				providerPerResource, amountPerResource, upm,
				ot, od);
	}
	
	public int getMaxNbUsersPerResource() {
		return maxNbUsersPerResource;
	}
	public int getMinNumUsersPerResource() {
		return minNbUsersPerResource;
	}
	public Set<ResourceType> getResourceTypes() {
		return 
				relativePrefPerResourceAllocation.keySet()
				.stream()
				.map(x->x.getResource().getResourceType())
				.collect(Collectors.toSet());
	}
	
	
	private Map<UserResourceTypeAllocation, Integer> cache=null;
	public Map<UserResourceTypeAllocation, Integer> getAllocationsPerResourceType() {
		/*BinaryOperator<Integer> bo = new BinaryOperator<Integer>() {
			@Override
			public Integer apply(Integer arg0, Integer arg1) {
				assert(arg0 == arg1);return arg0;
			}
		};*/
		
		if(cache==null)
			cache = relativePrefPerResourceAllocation.keySet()
				.stream()
				.collect(
						Collectors.toMap
						(
								x->UserResourceTypeAllocation.newInstance(x),
								x->relativePrefPerResourceAllocation.get(x),
								(x,y)->{assert(x.equals(y));return x;})
						);
		return cache;
	}
	
	public Set<User> getAllUsers() {
		return relativePrefPerResourceAllocation.keySet()
				.stream()
				.map(x->x.getUser())
				.collect(Collectors.toSet());
	}
	
	private static Map<UserResourceInstanceAllocation,Integer> 
	toRelativePreferences(
			Map<UserResourceTypeAllocation, Double> baseValues,
			UserPreferenceMeaning pt,
			Function<ResourceType, Integer> resourceInstancePerResourceType
			)
	{
		Set<User> requesters = 
				baseValues
				.keySet()
				.stream()
				.map(x->x.getUser())
				.collect(Collectors.toSet());
		
		Set<ResourceType> resources = 
				baseValues.keySet()
				.stream()
				.map(x->x.getResource())
				.collect(Collectors.toSet());
		
		Map<UserResourceInstanceAllocation, Integer> res = new HashMap<>();
		
		for(User s: requesters)
		{
			Map<ResourceType, Integer> orderedPreferences =
					getOrderedPreferencesFor(s, baseValues, resources, pt);
			
			for(ResourceType rt: resources)
				for(ResourceInstance ri: ResourceInstance.newInstancesFrom(rt, 
						resourceInstancePerResourceType.apply(rt)))
					res.put(UserResourceInstanceAllocation.newInstance(s, ri), 
							orderedPreferences.get(rt));

		}
		return res;
	}
	
	private static Map<ResourceType, Integer> getOrderedPreferencesFor(
			User s,
			Map<UserResourceTypeAllocation, Double> baseValues, 
			Set<ResourceType> resources,
			UserPreferenceMeaning meaning) {
		
		Supplier<Integer> incrementPerEachAddedResourceType= null;
		switch(meaning)
		{
		case PERSONAL_INSATISFACTION: incrementPerEachAddedResourceType = ()->1;break;
		case COMPARATIVE_INSATISFACTION:throw new Error();
		}
		
		Map<ResourceType, Integer> valuePerResourceType = new HashMap<>();
			
		//sort by decreasing order
		Set<UserResourceTypeAllocation> sortedPositiveValues = new TreeSet<>((x, y)-> {
			if(baseValues.get(x).equals(baseValues.get(y)))
				return x.toString().compareTo(y.toString());
			return -Double.compare(baseValues.get(x), 
					baseValues.get(y));});
		
		sortedPositiveValues.addAll(baseValues.keySet().stream()
				.filter(x->x.getUser().equals(s) && baseValues.get(x)> 0)
				.collect(Collectors.toSet()));
		
		
		
		Set<UserResourceTypeAllocation> sortedNegativeValues = new TreeSet<>(
				(x, y)-> {
					if(baseValues.get(x).equals(baseValues.get(y))) return x.toString().compareTo(y.toString());
					return -Double.compare(baseValues.get(x), 
							baseValues.get(y));});
		sortedNegativeValues.addAll(baseValues.keySet().stream()
				.filter(x->x.getUser().equals(s) && baseValues.get(x)< 0).collect(Collectors.toSet()));
				
		Set<ResourceType> nonAllocatedResources = new HashSet<>();
		nonAllocatedResources.addAll(resources);
		nonAllocatedResources.removeAll(sortedPositiveValues.stream().map(x->x.getResource()).collect(Collectors.toList()));
		nonAllocatedResources.removeAll(sortedNegativeValues.stream().map(x->x.getResource()).collect(Collectors.toList()));
		
		int currentClaimedInsatisfaction = 0;
		int currentPotentialInsatisfaction = 0;
		UserResourceTypeAllocation prev = null;
		for(UserResourceTypeAllocation a:sortedPositiveValues)
		{
			if(prev != null && !baseValues.get(a).equals(baseValues.get(prev)))
				currentClaimedInsatisfaction = currentPotentialInsatisfaction;
			prev = a;
			currentPotentialInsatisfaction+=incrementPerEachAddedResourceType.get();
			
			valuePerResourceType.put(a.getResource(), currentClaimedInsatisfaction);
		}
		
		currentClaimedInsatisfaction = currentPotentialInsatisfaction;
		for(ResourceType allocatee:nonAllocatedResources)
		{
			currentPotentialInsatisfaction+=incrementPerEachAddedResourceType.get();
			valuePerResourceType.put(allocatee, currentClaimedInsatisfaction);
		}
		currentClaimedInsatisfaction = currentPotentialInsatisfaction;
		for(UserResourceTypeAllocation a:sortedNegativeValues)
		{
			if(prev != null && !baseValues.get(a).equals(baseValues.get(prev)))
				currentClaimedInsatisfaction = currentPotentialInsatisfaction;
			prev = a;
			currentPotentialInsatisfaction+=incrementPerEachAddedResourceType.get();
			valuePerResourceType.put(a.getResource(), currentClaimedInsatisfaction);
		}
		
		return valuePerResourceType;
	}

	public Set<UserGroup> getUserGroups() {
		return userGroups;
	}

	public Set<UserResourceInstanceAllocation> getAllocationsForResource(ResourceInstance resource) 
	{
		return 
				getAllocationsInstances().stream()
				.filter(x->x.getResource().equals(resource))
				.collect(Collectors.toSet());
	}

	private Set<UserResourceInstanceAllocation> getAllocationsInstances() {
		
		return 
		getAllocationsPerResourceType()
		.keySet().stream()
		.map(x->UserResourceInstanceAllocation.newInstances(
				x.getUser(),
				ResourceInstance.newInstancesFrom(x.getResource(), 
						getAmountOfInstancesFor(x.getResource()))))
		.reduce(new HashSet<>(), (x,y)->{x.addAll(y); return x;});
	}
	
	

	private int getAmountOfInstancesFor(ResourceType resource) {
		return amountOfInstancesPerResourceType.apply(resource);
	}

	public ResourceOwner getOwner(ResourceType r) {
		ResourceOwner res =providerPerResource.apply(r);
		assert(res!=null);
		return res;
	}

	
	public Set<ResourceOwner> getAllResourceOwners() {
		return 
				relativePrefPerResourceAllocation.keySet()
				.stream()
				.map(x->providerPerResource.apply(x.getResource().getResourceType()))
				.collect(Collectors.toSet());
	}

	public Set<ResourceType> getResourcesTypesFrom(ResourceOwner rp) {
		return getResourceTypes()
				.stream().filter(x->providerPerResource.apply(x).equals(rp))
				.collect(Collectors.toSet());
	}
	
	public UserGroup getUserGroupOf(User u)
	{
		Set<UserGroup>groups = userGroups.stream().filter(x->x.getUsers().contains(u))
				.collect(Collectors.toSet());
		assert(groups.size()==1);
		return groups.iterator().next();
	}

	public static void checkCoherenceOf(ProblemInstance in) {
		if(in.getAllUsers().size() > in.getResourceTypes()
				.size()*in.getMaxNbUsersPerResource()) 
			throw new IllegalArgumentException("Not enough resources for satisfying all requesters. #requesters="+in.getAllUsers().size()
			+", #allocable_resources="+in.getResourceTypes().size()*in.getMinNumUsersPerResource()+" (#resources:"
					+in.getResourceTypes().size()+" x #userPerResource:"+in.getMaxNbUsersPerResource()+")"
			+"\nUsers:" +in.getAllUsers()+"\nResources:"+in.getResourceTypes());
	}

	
	


	

	public static ProblemInstance newInstance(String[] args) {
		InputBuilder ib = InputBuilder.parse(args);
		return ProblemInstance.newInstance(ib);
	}

	private static ProblemInstance newInstance(InputBuilder ib) {
		if(!ib.has(ParameterTypes.PREFERENCE_FILE))
			throw new Error("Please enter as input the preference file");
		Map<UserResourceTypeAllocation, Double> baseValues = 
				InputParser.parseUserPreference(
						ib.get(ParameterTypes.PREFERENCE_FILE));
		
		int minNbUsersPerResource = Integer.parseInt(ib.get(ParameterTypes.MIN_NB_USER_PER_RESOURCE));
		int maxNbUsersPerResource = Integer.parseInt(ib.get(ParameterTypes.MAX_NB_USER_PER_RESOURCE));
		
		Set<UserGroup> userGroups = InputParser.parseUserGroups(ib.get(ParameterTypes.PREFERENCE_FILE));
		
		Function<ResourceType, ResourceOwner> providerPerResource =
				InputParser.parseProviderPerResource(ib.get(ParameterTypes.RESOURCE_OWNERSHIP_MODE));

		Function<ResourceType,Integer> amountPerResource = 
				InputParser.parseAmountPerResource(ib.get(ParameterTypes.RESOURCE_DUPLICATE_MODE));
		UserPreferenceMeaning upm = InputParser.parseUserPreferenceMeaning(ib.get(ParameterTypes.PREFERENCE_MEANING));
		
		OwnerDesire desire = OwnerDesire.NO_DESIRE;
		if(ib.has(ParameterTypes.OWNER_DESIRES))
			desire = OwnerDesire.valueOf(ib.get(ParameterTypes.OWNER_DESIRES));
		
				
		return newInstance(
				baseValues,
				minNbUsersPerResource,
				maxNbUsersPerResource, 
				userGroups, 
				providerPerResource, 
				amountPerResource,
				upm,
				OutputType.valueOf(ib.get(ParameterTypes.OUTPUT_MODE)),
				desire
				)
				;
		
	}

	public Set<UserResourceInstanceAllocation> getAllocationsFilteredBy(int maxInsatisfaction)
	{
		return getAllocationsInstances()
				.stream()
				.filter(x->getInsatisfactionFor(x)<=maxInsatisfaction)
				.collect(Collectors.toSet());
	}

	public int getInsatisfactionFor(UserResourceInstanceAllocation alloc) {
		assert(relativePrefPerResourceAllocation.containsKey(alloc));
		return relativePrefPerResourceAllocation.get(alloc);

	}

	public int getInsatisfactionFor(UserResourceTypeAllocation alloc) {
		return getInsatisfactionFor(UserResourceInstanceAllocation.newInstance(
				alloc.getUser(), ResourceInstance.newInstance(alloc.getResource(),0)));
	}

	public Map<UserResourceInstanceAllocation, Integer> getRelativeInsatisfactionFor(
			Set<UserResourceInstanceAllocation> allAdmissibleAllocations) {
		return
				allAdmissibleAllocations.stream()
				.collect(
						Collectors.toMap(Function.identity(), 
								x->getInsatisfactionFor(x)));
	}

	public Set<UserResourceInstanceAllocation> getHardConstraints() {
		return new HashSet<>();
	}

	public Set<UserResourceInstanceAllocation> getResouceInstanceAllocationsFor(User u) {
		return getAllocationsInstances()
				.stream()
				.filter(x->x.getUser().equals(u))
				.collect(Collectors.toSet());
	}

	public Set<ResourceInstance> getResourceInstancesFrom(ResourceOwner rp) {
		return getResourcesTypesFrom(rp)
				.stream()
				.map(x->ResourceInstance.newInstancesFrom(x, amountOfInstancesPerResourceType.apply(x)))
				.reduce(new HashSet<ResourceInstance>(), (x,y)->{x.addAll(y); return x;});
	}

	public Map<UserResourceInstanceAllocation, Integer> getAllocationsPerResourceInstance() {
		return relativePrefPerResourceAllocation;
	}

	public ResourceOwner getOwner(ResourceInstance s) {
		return getOwner(s.getResourceType());
	}
	
	public enum OutputType {LATEX_REPORT, CONSOLE_PRINT}

	public OutputType getOutputType() {
		return ot;
		
	}

	public boolean isDebugPrint() {
		return true;
	}

	public Map<ResourceOwner, Set<ResourceInstance>> getOwnershipForInstances(
			Set<ResourceInstance> instances) {
		
		Set<ResourceOwner>owners = instances.stream().map(x->getOwner(x))
		.collect(Collectors.toSet());
		Map<ResourceOwner, Set<ResourceInstance>> res = new HashMap<ResourceOwner, Set<ResourceInstance>>();
		for(ResourceOwner ro: owners)
			res.put(ro, instances.stream()
					.filter(x->getOwner(x).equals(ro)).collect(Collectors.toSet()));
			
		return res;
	}

	public OwnerDesire getOwnerAllocationPreferences() {
		return od;
	}

	public boolean isMinimizingTheWorkloadOfTheMostLoaded() {
		return false;
	}
}
