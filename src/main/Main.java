package main;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.swing.event.ListSelectionEvent;

import ilog.concert.IloNumVar;
import input.ProblemInstance;
import model.UserResourceTypeAllocation;
import output.Printer;
import solver.SatisfactionMeasure;
import solver.Solver;
import solver.UserResourceInstanceAllocation;

public class Main {
	
	/**
	 * 
	 * 
	 * Command line example:
	 * PREFERENCE_FILE:input.csv MIN_NB_USER_PER_RESOURCE:4 MAX_NB_USER_PER_RESOURCE:4 RESOURCE_PER_OWNER:projects_by_tutor.csv AMOUNT_PER_RESOURCE:nb_proj.csv PREFERENCE_MEANING:PERSONAL_INSATISFACTION
	 *  
	 * Java parameters example:
	 * -ea -Djava.library.path=/export/home/vanhee/Documents/software/cplex/cplex_studio/cplex/bin/x86-64_linux
	 *  
	 * 
	 * Contents of input.csv:
	 * RESOURCE_TYPE;USERS;PREFERENCE
	 * USERS->USER | USER,USERS
	 * RESOURCE_TYPE: any string without special characters
	 * USER: any string without special characters
	 * PREFERENCE: any integer 
	 * 
	 * Such a line means "the users grades this resource type with the given preference".
	 * The higher preference, the more preferred/important.
	 * In the program, preferences are made relative (preferred, second preferred...).
	 * 
	 * Contents of projects_by_tutor.csv:
	 * NAME;RESOURCES*
	 * RESOURCES->RESOURCE_TYPE;RESOURCES
	 * RESOURCES->RESOURCE_TYPE
	 * RESOURCE_TYPE is a resource as defined in input.csv
	 * NAME is any string without special characters
	 * 
	 * This file indicate the set of resource providers and the resource types
	 * they provide. 
	 * 
	 * Contents of nb_proj.csv:
	 * RESOURCE;AMOUNT
	 * RESOURCE is a resource as defined in input.csv
	 * AMOUNT in a positive
	 * 
	 * This file indicates the number of resource instance per resource type.
	 *  
	 * The program computes an allocation such that minimizes the insatisfaction
	 * of the most unsatisfied users while:
	 * --having at least MIN_NB_USER_PER_RESOURCE per resource instance
	 *  (except for the "remainder" if any) (to be expanded to uneven group size)
	 * --having at most MAX_NB_USER_PER_RESOURCE per resource
	 * --consuming less resource instances than the number offered by a resource provider
	 * --minimizing the consumption per resource provider (to be expanded)
	 * --never splitting groups
	 * 
	 * The preference insatisfaction is represented by the tuple
	 * (p1, p2, ... pn) where pk means that n users were given their kth preference.
	 * The aim is to first minimize pn, then given a minimal pn, minimize p(n-1)...
	 * Two interpretations for user insatisfaction are given:
	 * PERSONAL_INSATISFACTION i.e. the degree of satisfaction of the resource type 
	 * acquired by the user
	 * COMPARATIVE_INSATISFACTION i.e. the amount of people who got preferred resource 
	 * instances
	 * Comparative insatisfaction integrates the abundance of a (denied) resource type
	 * as an (aggravating) factor on user satisfaction. Being denied an abundant 
	 * resource is considered as "more unfair".
	 * 
	 *  
	 * @param args
	 */
	public static void main(String[] args)
	{		
		ProblemInstance input = ProblemInstance.newInstance(args);
		
		Printer.displayInstanceStats(input);
		
		System.out.println();
		
		Set<UserResourceInstanceAllocation> res = Solver.optimize(input);
		
		Printer.printOutput(res, input.getAllocationsPerResourceInstance());
	
		Printer.processResults(res, input);			
	}

	

}