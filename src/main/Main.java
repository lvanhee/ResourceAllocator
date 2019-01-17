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
	 * -ea -Djava.library.path=/export/home/vanhee/Documents/software/cplex/cplex_studio/cplex/bin/x86-64_linux
	 * 
	 * Input:
	 * inputfile min_user_per_resource max_user_per_resource
	 * 
	 * Two file formats, based on CSV are accepted. 
	 * The first is for decoupled resources and users. The file format is:
	 * resource;user*;preference
	 * where user* is any sequence of user
	 * 'resource' and 'user' are free strings, without ";" 
	 * preference is an integer
	 * Such a line means "the user grades this resource with the given preference".
	 * Higher preference means more preferred/important
	 * 
	 * Groups can be filled in
	 * In that case, we assume that 
	 * -each resource can be acquired by two users
	 * -each resource must be either acquired by zero or two users
	 * -pairs cannot be split
	 * -single users must be paired together
	 *  
	 *  PREFERENCE_FILE:input.csv MIN_NB_USER_PER_RESOURCE:4 MAX_NB_USER_PER_RESOURCE:4 RESOURCE_PER_OWNER:projects_by_tutor.csv AMOUNT_PER_RESOURCE:nb_proj.csv PREFERENCE_MEANING:PERSONAL_INSATISFACTION
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