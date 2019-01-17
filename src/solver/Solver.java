package solver;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import ilog.concert.IloException;
import ilog.concert.IloIntExpr;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplexModeler;
import input.ProblemInstance;
import model.ResourceType;
import model.ResourceOwner;
import model.User;
import model.UserResourceTypeAllocation;
import model.UserGroup;
import output.Printer;

public class Solver {
	
	public enum ConstraintsOnResourceOwners{
		NO_CONSTRAINTS,	MINIMIZE_RESOURCE_CONSUMMED_PER_OWNER;}


	public static Optional<Set<UserResourceInstanceAllocation>> 
	optimizeAccordingToMaxInsatisfaction(
			int maxInsatisfaction,
			ProblemInstance inF,
			int maxNbResourcePerOwner)
	{
	
		try {
			IloCplex cplex = new IloCplex();
			cplex.setOut(null);

			Set<UserResourceInstanceAllocation> allAdmissibleAllocations = 
					inF.getAllocationsFilteredBy(maxInsatisfaction);

			SortedSet<User> users = 
					new TreeSet<>((x,y)->x.toString().compareTo(y.toString()));
			users.addAll(inF.getAllUsers());

			SortedSet<ResourceInstance> allAdmissibleResourceInstances = 
					new TreeSet<>((x,y)->x.toString().compareTo(y.toString()));

			allAdmissibleResourceInstances.addAll(
					allAdmissibleAllocations
					.stream().map(x->x.getResource())
					.collect(Collectors.toSet()));
			
			Map<UserResourceInstanceAllocation, IloIntVar> allocToVar = 
					generateVariablePerAllocation(cplex,
					allAdmissibleAllocations
					);
			
			
			Map<ResourceInstance, IloIntVar> resourceMinLiftingJokerVars = 
					allAdmissibleResourceInstances.stream().collect(
							Collectors.toMap(Function.identity(),
									x->
									{
										try {
											return cplex
													.boolVar("jokerForDiscardingTheMinAllocationConstraint("+x+")"
													);
										} catch (IloException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
											throw new Error();
										}
									}));
	
			for(User pl:users)
			{
				int worseAllocValue = 1;
				if(allAdmissibleAllocations.stream()
						.anyMatch(x-> x.getUser().equals(pl)))
				{
					UserResourceInstanceAllocation worseAlloc = 
							allAdmissibleAllocations.stream()
							.filter(x-> x.getUser().equals(pl))
							.max((x,y)->inF.getInsatisfactionFor(x)-
									inF.getInsatisfactionFor(y)).get();
					worseAllocValue = inF.getInsatisfactionFor(worseAlloc);
				}
					
				/*@Deprecated
				 * for(UserResourceInstanceAllocation a: allAdmissibleAllocations)
					if(!allAdmissibleAllocations.contains(a) && a.getUser().equals(pl))
						inF.getAllocations().put(a, worseAllocValue+1);*/
			}
			
			Map<ResourceInstance, IloIntVar> varPerResource = new HashMap<>();
			for(ResourceInstance r: allAdmissibleResourceInstances)
			{
				try {
					varPerResource.put(r, cplex
							.boolVar("ActiveResourceVar("+r+")"
							));
				} catch (IloException e) {e.printStackTrace();throw new Error();}
			}

			cplex.addMinimize(
					Solver.getExpressionToMinimize(
							cplex, 
							allAdmissibleAllocations,
							users,
							inF.getRelativeInsatisfactionFor(allAdmissibleAllocations),
							allocToVar));
			
			
			generateAllocationConstraints(
					cplex,
					allAdmissibleAllocations,
					allAdmissibleResourceInstances,
					allocToVar,
					varPerResource,
					resourceMinLiftingJokerVars,
					inF, maxNbResourcePerOwner
					);
			
			generateHardAllocationsConstraints(cplex, allocToVar, inF.getHardConstraints());
	
			cplex.exportModel("mipex1.lp");
	
			if (! cplex.solve() )
				return Optional.empty();
			System.out.println("Solution status = " + cplex.getStatus());
			Set<UserResourceInstanceAllocation>s=
					processCplexResults(cplex, allocToVar);
			
			
	
			//s.sort((x,y)->roles.indexOf(x.role) - roles.indexOf(y.role));
			System.out.println("Minimizing the number of least happy people:");
			cplex.end();
			
			return Optional.of(s);
	
		}
		catch (IloException e) {
			System.err.println("Concert exception caught '" + e + "' caught");
			throw new Error();
		}
	}
	

	private static void addConstraintsOnResourceOwners(
			IloCplex cplex, 
			SortedSet<ResourceInstance> allAdmissibleResources, 
			Set<UserResourceInstanceAllocation> allAdmissibleAllocations,
			ProblemInstance inF, 
			int maxNbResourcePerOwner, 
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc,
			Map<ResourceInstance,IloIntVar> varPerResource) throws IloException {

		for(ResourceOwner rp : inF.getAllResourceOwners())
		{
			IloNumExpr countResourcesAllocatedForRP = cplex.constant(0);

			for(ResourceInstance r: inF.getResourceInstancesFrom(rp)
					.stream()
					.filter(x->allAdmissibleResources.contains(x))
					.collect(Collectors.toSet()))
				countResourcesAllocatedForRP = cplex.sum(
						countResourcesAllocatedForRP, varPerResource.get(r));
			cplex.addGe(
					maxNbResourcePerOwner, 
					countResourcesAllocatedForRP, 
					"AllocateResourceFromAllocatorAtMost"+maxNbResourcePerOwner+"Times("
							+rp+","+inF.getMinNumUsersPerResource()+")");
		}
	}
	


	
	

	public static Set<UserResourceInstanceAllocation> optimize(ProblemInstance input) {
		
		Set<UserResourceInstanceAllocation> optimal = 
				optimalAllocationMinimizingUserInsatisfaction(input);
		
		checkAllocation(optimal,input);
		
		Printer.printOutput(optimal, input.getAllocationsPerResourceInstance());
		Printer.processResults(optimal, input);	
		
		SatisfactionMeasure sm = SatisfactionMeasure.newInstance(optimal, input);
		
		optimalAllocationMatchingSatisfactionMeasureAndMinimizingResourceOwnerLoad(
				input,
				sm);
		
		return optimal;
	
	}

	private static void checkAllocation(
			Set<UserResourceInstanceAllocation> optimal,
			ProblemInstance input) {
		for(UserGroup ug: input.getUserGroups())
		{
			Map<User,ResourceInstance> allocatedResourcesFor=
					optimal.stream()
					.filter(x->ug.getUsers().contains(x.getUser()))
					.collect(Collectors.toMap(x->x.getUser(),
							x->x.getResource())
							);
			assert(allocatedResourcesFor.values().stream()
					.collect(Collectors.toSet()).size()==1);
		}
	}


	private static Set<UserResourceInstanceAllocation> optimalAllocationMinimizingUserInsatisfaction(ProblemInstance input)
	{
		Optional<Set<UserResourceInstanceAllocation>> res = Optional.empty();

		for(int i = 1 ; i <= input.getAllUsers().size()&&!res.isPresent(); i++)
		{
			System.out.println(
					"Trying to find an allocation with a maximum "
							+ "least satisfaction of rank:"+i);

			res= Solver.optimizeAccordingToMaxInsatisfaction(
					i,
					input,Integer.MAX_VALUE);
		}

		return res.get();
	}


	private static Set<UserResourceInstanceAllocation> optimalAllocationMatchingSatisfactionMeasureAndMinimizingResourceOwnerLoad(ProblemInstance input,
			SatisfactionMeasure smToReach) {
		Optional<Set<UserResourceInstanceAllocation>> res = Optional.empty();
		
		int worseInsatisfactionToMeet = smToReach.getWorseAllocationValue();
			
			
		
		for(int i = 1 ; i <= input.getAllUsers().size()&&!res.isPresent(); i++)
		{
			System.out.println(
					"Trying to find an allocation with a maximum "
					+ "least satisfaction of rank:"+worseInsatisfactionToMeet+
					" and a maximum number of allocations per owner of:"
					+i
					);
			
			res= Solver.optimizeAccordingToMaxInsatisfaction(
					worseInsatisfactionToMeet,
					input,i);
			
			if(res.isPresent())
				if(smToReach.equals(SatisfactionMeasure.newInstance(res.get(),input)))
					return res.get();
				else 
				{
					Printer.printOutput(res.get(), input.getAllocationsPerResourceInstance());
					Printer.processResults(res.get(), input);			
				}
				res = Optional.empty();
		}
		
		return res.get();
	}



	static void generateHardAllocationsConstraints(
			IloCplex cplex, 
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc,
			Set<UserResourceInstanceAllocation> hardConstraints) throws IloException {
		for(UserResourceInstanceAllocation hc: hardConstraints)
			cplex.addEq(1,
					varPerAlloc.get(hc), "HardConstraint("+hc+")");
	}



	static Map<UserResourceInstanceAllocation, IloIntVar> generateVariablePerAllocation(
			IloCplex cplex,
			Collection<UserResourceInstanceAllocation> consideredAllocations
			) throws IloException {
		Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc = new HashMap<>();
		
		for(UserResourceInstanceAllocation pl : consideredAllocations)
				varPerAlloc.put(pl,
						cplex.boolVar(
						//cplex.numVar(0, 1,
								pl.toString()));
		
		return varPerAlloc;
	}



	static Set<UserResourceInstanceAllocation> processCplexResults(
			IloCplex cplex, 
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc) {
		return varPerAlloc.keySet().stream()
		.filter(x->{
			try {
				return cplex.getValue(varPerAlloc.get(x))>0.01;
			} catch (IloException e) {
				e.printStackTrace();
				throw new Error();
	
			}
		})
		.collect(Collectors.toSet());
	}
	
	public static void generateAllocationConstraints(
			IloCplex cplex, 
			Set<UserResourceInstanceAllocation> allAdmissibleAllocations, 
			SortedSet<ResourceInstance> allAdmissibleResources,
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc,
			Map<ResourceInstance, IloIntVar> allocatedResourceVar,
			Map<ResourceInstance, IloIntVar> allocationJoker,
			ProblemInstance inF,
			int maxNbResourcePerOwner
			)
					throws IloException{
		
		connectResourcesAndAllocations(cplex,
				inF, 
				allocatedResourceVar,
				varPerAlloc
				);
		
		matchAllocationsOfGroups(cplex, inF, varPerAlloc);
		
		Set<UserResourceInstanceAllocation>validAllocations = varPerAlloc.keySet();
		for(User pl: inF.getAllUsers())
		{
			IloNumExpr oneResourcePerUser = 
					cplex.constant(0);
			for(UserResourceInstanceAllocation al: inF.getResouceInstanceAllocationsFor(pl))
			{
				if(!validAllocations.contains(al))continue;
				oneResourcePerUser = 
						cplex.sum(
						oneResourcePerUser,
						varPerAlloc.get(al));
			}
			cplex.addEq(
					1,
					oneResourcePerUser,
					"EachUserIsGivenExactlyOneResource("+pl+")");
		}

		allocateEachResourceInstanceAtMostKTimes(cplex, 
				inF, 
				varPerAlloc, 
				allAdmissibleResources,
				validAllocations);
		
		for(UserResourceInstanceAllocation a:allAdmissibleAllocations)
		{
			cplex.addGe(
					allocatedResourceVar.get(a.getResource()),
					varPerAlloc.get(a),
					"CountsAsAllocatedIfAllocatedFor("+a.getResource()+","+a+")");
		}	
		
		for(ResourceInstance resource: allAdmissibleResources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserResourceInstanceAllocation s: inF.getAllocationsForResource(resource)
					.stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(s));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMaxNbUsersPerResource(),
							allocatedResourceVar.get(resource)));
			
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMaxNbUsersPerResource(),
							allocationJoker.get(resource)));
					
			cplex.addLe(
					countUsersPerResource,
					0, 
					"EachNonAllocatedResourceIsAllocatedAtMost0TimesUnlessTheJokerIsUsed("+resource+")");
		}
		
		for(ResourceInstance resource: allAdmissibleResources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserResourceInstanceAllocation ua: inF.getAllocationsForResource(resource).stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(ua));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMinNumUsersPerResource(),
							allocatedResourceVar.get(resource)));
			
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(inF.getMinNumUsersPerResource(),
							allocationJoker.get(resource)));
					
			cplex.addGe(
					countUsersPerResource,
					0, 
					"EachAllocatedResourceMustBeAllocatedAtLeastKTimesUnlessTheJokerIsUsed("+resource+")");
		}
		
		
		IloNumExpr allJokers = cplex.constant(0);
		for(IloIntVar joker: allocationJoker.values())
			allJokers = cplex.sum(allJokers, joker);
		cplex.addLe(allJokers, 1,
				"AtMostOneJoker");
		
		for(ResourceInstance resource: allAdmissibleResources.stream()
				.filter(x->allAdmissibleAllocations.contains(x))
				.collect(Collectors.toSet()))
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserResourceInstanceAllocation s: inF.getAllocationsForResource(resource))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(s));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMinNumUsersPerResource(),
							allocatedResourceVar.get(resource)));
					
			cplex.addGe(
					0, 
					countUsersPerResource, 
					"EachAllocatedResourceIsAllocatedAtLeastKTimes("
					+resource+","+inF.getMinNumUsersPerResource()+")");
		}
		
		


		//ensure pairs
		for(UserGroup userGroup: inF.getUserGroups())
			for(ResourceInstance resource: allAdmissibleResources.stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				User u0 = userGroup.getUsers().iterator().next();
				UserResourceInstanceAllocation u0PicksR=
						UserResourceInstanceAllocation.newInstance(u0, resource);
				
				if(!varPerAlloc.containsKey(u0PicksR)) continue;

				for(User u1: userGroup.getUsers())	
				{
					UserResourceInstanceAllocation u1PicksR= 
							UserResourceInstanceAllocation.newInstance(u1, resource);

					IloNumExpr exprUser0PicksR =
							varPerAlloc.get(u0PicksR);

					IloNumExpr exprUser1PicksR =
							varPerAlloc.get(u1PicksR);

					cplex.addEq(exprUser0PicksR, 
							exprUser1PicksR,
							"PairedUsersMatchTheirDecisions("
									+u0+","
									+u1+","+
									resource+")");
				}
			}
		
		addConstraintsOnResourceOwners(
				cplex,
				allAdmissibleResources,
				allAdmissibleAllocations,
				inF,
				maxNbResourcePerOwner, varPerAlloc, allocatedResourceVar);
		
		
		//ensure adequate resource consumption
		/*for(String resource:resources)
		{
			IloIntVar usingRKTimes = cplex.boolVar();
			IloNumExpr total = cplex.prod(usingRKTimes, inF.numberOfUsersPerResource);
			for(Allocation a: 
				varPerAlloc.keySet().stream()
				.filter(x->x.resource.equals(resource))
				.collect(Collectors.toSet()))
			{
				total = cplex.sum(total, varPerAlloc.get(a));
			}
			cplex.addEq(total, inF.numberOfUsersPerResource, "FullAllocationOfResource("+resource+")");
		}*/
		
		
	}


	private static void allocateEachResourceInstanceAtMostKTimes(
			IloCplexModeler cplex, ProblemInstance inF,
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc,
			Set<ResourceInstance> allAdmissibleResources,
			Set<UserResourceInstanceAllocation> allAdmissibleAllocations) throws IloException {
		for(ResourceInstance resource: allAdmissibleResources)
		{
			IloNumExpr maxKUsersPerResource = cplex.constant(0);
			for(UserResourceInstanceAllocation ua: inF.getAllocationsForResource(resource)
					.stream()
					.filter(x->allAdmissibleAllocations.contains(x))
					.collect(Collectors.toSet()))
			{
				
				maxKUsersPerResource = cplex.sum(
						maxKUsersPerResource, varPerAlloc.get(ua));
			}
			cplex.addGe(
					inF.getMaxNbUsersPerResource(), 
					maxKUsersPerResource, 
					"EachResourceIsAllocatedAtMostKTimes("+resource+","+
					inF.getMaxNbUsersPerResource()+")");
		}
	}


	private static void matchAllocationsOfGroups(IloCplex cplex, ProblemInstance inF,
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc
			) throws IloException {
		
		Map<UserGroup,Map<ResourceInstance,IloIntVar>> varPerResourcePerGroup = 
				new HashMap<>();

		for(UserGroup ug: inF.getUserGroups())
		{
			
			varPerResourcePerGroup.put(ug, new HashMap<>());
			for(ResourceInstance r: 
				varPerAlloc.keySet()
				.stream()
				.filter(x->ug.getUsers().contains(x.getUser()))
				.map(x->x.getResource())
				.collect(Collectors.toSet())
					)
			{
				varPerResourcePerGroup.get(ug).put(r, cplex.boolVar(
						"isResourceAllocatedToGroup("+
								r+","+ug+")"));
			}
			for(UserResourceInstanceAllocation ua:
				varPerAlloc.keySet().stream()
				.filter(x->ug.getUsers().contains(x.getUser()))
				.collect(Collectors.toSet()))
			{
				cplex.addEq(varPerResourcePerGroup.get(ug).get(ua.getResource()),
						varPerAlloc.get(ua),
						"allMemberOfUserGroupsMustBeAllocatedTheSameResource("+ua+","+ug+")"
						);
			}
		}
	}


	private static void connectResourcesAndAllocations(IloCplex cplex, ProblemInstance inF,
			Map<ResourceInstance, IloIntVar> allocatedResourceVar, 
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc) throws IloException 
	{
		for(ResourceInstance r: allocatedResourceVar.keySet())
		{
			IloNumExpr allAllocations = cplex.constant(0);
			
			Set<UserResourceInstanceAllocation> allocationsForResource = 
					varPerAlloc.keySet()
					.stream()
					.filter(x->x.getResource().equals(r))
					.collect(Collectors.toSet());
					
			for(UserResourceInstanceAllocation ua:allocationsForResource)
				allAllocations = cplex.sum(allAllocations, varPerAlloc.get(ua));
			
			IloNumExpr resource = 
					cplex.prod(allocationsForResource.size(), allocatedResourceVar.get(r));
			
			cplex.addLe(allAllocations, resource,
					"AllocationsMakeResourceConsummed("+r+")");
		}
	}


	public static IloNumExpr getExpressionToMinimize(
			IloCplex cplex, 
			Set<UserResourceInstanceAllocation> allowedAllocations, 
			Set<User> users,
			Map<UserResourceInstanceAllocation, Integer> prefsPerAllocation,
			Map<UserResourceInstanceAllocation, IloIntVar> varPerAlloc
			) throws IloException {
		IloNumExpr exprToOptimize =  cplex.constant(0);
		for(UserResourceInstanceAllocation a:allowedAllocations)
		{
			exprToOptimize = 
					cplex.sum(
							exprToOptimize,
							cplex.prod(
									Math.pow(users.size(), prefsPerAllocation.get(a))+1,
									varPerAlloc.get(a)));
		}
		IloNumExpr[] weightedAllocations = new IloIntExpr[prefsPerAllocation.size()];
	
		int i = 0;
		for(UserResourceInstanceAllocation a: varPerAlloc.keySet())
		{
			weightedAllocations[i] = cplex.prod(
					varPerAlloc.get(a),
					prefsPerAllocation.get(a));
			i++;
		}
		return exprToOptimize;
	}


}
