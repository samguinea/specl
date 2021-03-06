package it.polimi.specl;

import it.polimi.specl.assertions.AssertionService;
import it.polimi.specl.assertions.AssertionServiceImpl;
import it.polimi.specl.dataobject.DataObject;
import it.polimi.specl.dataobject.DataObjectImpl;
import it.polimi.specl.declaration.DeclarationService;
import it.polimi.specl.declaration.DeclarationServiceImpl;
import it.polimi.specl.helpers.StringHelper;
import it.polimi.specl.helpers.VariablesHelper;
import it.polimi.specl.helpers.SpeclException;
import it.polimi.specl.specl.Assertion;
import it.polimi.specl.specl.AssertionForm;
import it.polimi.specl.specl.Assertions;
import it.polimi.specl.specl.Declaration;
import it.polimi.specl.specl.Model;
import it.polimi.specl.specl.Step;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EObjectContainmentEList;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.w3c.dom.Document;

import com.google.common.collect.LinkedHashMultimap;
import com.google.inject.Injector;

/**
 * Specl Analyzer provides methods for setup the input file and the Specl assertions to check.
 * With <i>evaluate()</i> method the assertions are verified.
 * It's offering methods for retrieve the declared variables by its name, and handle them outside the analyzer.
 * 
 * @author Riccardo Brunetti
 */
public class SpeclAnalyzer {
	
	private Injector injector;

	private static Logger logger = Logger.getRootLogger();
	
	private String speclFilePath;
	private static DataObject input;
	
	private AssertionService assertionService;
	private DeclarationService declarationService;
	
	public SpeclAnalyzer() {
		assertionService = new AssertionServiceImpl();
		declarationService = new DeclarationServiceImpl(assertionService);
		
		input = null; // each new analyzer has it's own input file (note that it's static)
		new VariablesHelper(); //initialize the static map for variables
		
		logger.removeAllAppenders();
		logger.setLevel(Level.INFO);
		logger.addAppender(new ConsoleAppender(new PatternLayout("%5p - %m%n")));
		
		if(logger.isInfoEnabled()){
			logger.info("***** Specl Analyser *****");
		}
	}
	
	/**
	 * Set the XML {@link Document} input file to parse to a {@link DataObject}
	 * 
	 * @param doc the XML to parse
	 */
	public void setXMLInput(Document doc) {
		input = new DataObjectImpl(doc);
	}
	
	/**
	 * Set the JSON input to parse to a {@link DataObject}
	 * 
	 * @param json
	 */
	public void setJSONInput(String json) {
		input = new DataObjectImpl(json);
	}
	
	/**
	 * Set the map to assign to a {@link DataObject}
	 * @param map
	 */
	public void setMapInput(LinkedHashMultimap<String, Object> map) {
		input = new DataObjectImpl(map);
	}
	
	/**
	 * Set the path of the Specl assertions file
	 * 
	 * @param path the path of the file to parse
	 */
	public void setSpeclFilePath(String path) {
		speclFilePath = path;
	}
	
	/**
	 * Evaluate the assertions by passing Specl assertions
	 * 
	 * @param Specl the string containing the Specl assertions
	 * @return <code>true</code> if the assertions are respected, <code>false</code> otherwise
	 * @throws SpeclException if the input is not found or not defined
	 * @throws SpeclException if the assertions are not found or not defined
	 * @throws SpeclException if the evaluation goes wrong
	 */
	public boolean evaluate(String specl) throws SpeclException {
		
		injector = new it.polimi.specl.SpeclStandaloneSetupGenerated().createInjectorAndDoEMFRegistration();
		
		if(input == null) {
			throw new SpeclException("Input not found or not defined");
		}
		File f;
		
		XtextResourceSet resSet = injector.getInstance(XtextResourceSet.class);
		resSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
		Resource res;
		
		if(specl != ""){
			InputStream in = new ByteArrayInputStream(specl.getBytes());
			f = new File("__assertions.specl");
			res = resSet.createResource(URI.createURI(f.toURI().toString()));
			try {
				res.load(in, resSet.getLoadOptions());
			} catch (IOException e) {
				throw new SpeclException(e);
			}
		} else if(speclFilePath != null){
			f = new File(speclFilePath);
			res = resSet.getResource(URI.createURI(f.toURI().toString()), true);
		} else {
			String msg = "Assertions not found or not defined. Please pass a string or the path of the file containing them.";
			logger.error(msg);
			throw new SpeclException(msg);
		}
		
		return this.runGenerator(res);
	}
	
	/**
	 * Evaluate the assertions from previously assigned file 
	 * 
	 * @return <code>true</code> if the assertions are respected, <code>false</code> otherwise
	 * @throws SpeclException if the input is not found or not defined
	 * @throws SpeclException if the assertions are not found or not defined
	 */
	public boolean evaluate() throws SpeclException {
		return evaluate("");
	}
	
	private boolean runGenerator(Resource resource) throws SpeclException {
		// load the resource and parse it
//		ResourceSet set = resourceSetProvider.get();
//		Resource resource = set.getResource(URI.createURI(string), true);

		// check for syntax errors
		if (syntaxErrors(resource)) {
			System.exit(0);
		}

		// get contents
		Model model = (Model) resource.getContents().get(0);
		EObjectContainmentEList<Declaration> declarations = (EObjectContainmentEList<Declaration>) model.getDeclarations();
		Assertions assertionSet = model.getAssertionSet();
		
		if(logger.isDebugEnabled()) {
			// print out the DataObject conversion of the XML file
			logger.debug("INPUT: " + input);
		}
		
		if(logger.isInfoEnabled()){
			logger.info(declarations.size() + " declarations found");
			logger.info(assertionSet.eContents().size() + " assertions found"); //TODO
		}

		// get variables declaration and sets the hashmap
		try {
			declarationService.setVariable(declarations);
		} catch (Exception e) {
			logger.error(e.getMessage());
			throw new SpeclException(e.getMessage());
		}

		// verify the assertions
		boolean result = false;
		try {
			if(logger.isInfoEnabled()){
				logger.info("ASSERTION EVALUATION");
			}
			result = assertionService.verifyAssertions(assertionSet);
			if(logger.isInfoEnabled()){
				logger.info("RESULT: " + result);
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			throw new SpeclException(e.getMessage());
		}
		return result;
	}

	/**
	 * Syntax error checking, with information about the error (an error message, the line number of the error and wrong token)
	 * 
	 * @param resource
	 *            the result of the parsed rules
	 * @return <code>true</code> if there is errors, <code>false</code> otherwise
	 */
	private boolean syntaxErrors(Resource resource) {
		// syntax errors checking
		Iterable<INode> errors = ((XtextResource) resource).getParseResult().getSyntaxErrors();
		Iterator<INode> iter = errors.iterator();
		int number = resource.getErrors().size();

		if (number == 0) { // no errors, go away!
			return false;
		}

		logger.error("SYNTAX ERRORS: " + number + " found");

		INode errorNode = null;
		while (iter.hasNext()) {
			errorNode = iter.next();
			ICompositeNode parent = errorNode.getParent();
			EObject semanticElement = errorNode.getSemanticElement();
			int sl = errorNode.getStartLine();
			SyntaxErrorMessage errm = errorNode.getSyntaxErrorMessage();
			String erroneousToken = "";
			if (semanticElement instanceof AssertionForm) {
				erroneousToken = StringHelper.assertionFormToString((AssertionForm) semanticElement);
			} else if (semanticElement instanceof Assertion) {
				erroneousToken = StringHelper.assertionToString((Assertion) semanticElement);
			} else if (semanticElement instanceof Declaration) {
				erroneousToken = StringHelper.declarationToString((Declaration) semanticElement);
			} else if (semanticElement instanceof Step) {
				erroneousToken = StringHelper.stepToString((Step) semanticElement);
			} else {
				EObject parentSemanticElement = parent.getSemanticElement();
				while (erroneousToken.equals("")) {
					if (parentSemanticElement instanceof AssertionForm) {
						erroneousToken = StringHelper.assertionFormToString((AssertionForm) parentSemanticElement);
					} else if (parentSemanticElement instanceof Assertion) {
						erroneousToken = StringHelper.assertionToString((Assertion) parentSemanticElement);
					} else if (parentSemanticElement instanceof Declaration) {
						erroneousToken = StringHelper.declarationToString((Declaration) parentSemanticElement);
					} else if (parentSemanticElement instanceof Step) {
						erroneousToken = StringHelper.stepToString((Step) parentSemanticElement);
					} else {
						if (parentSemanticElement.eContainer() != null) {
							parentSemanticElement = parentSemanticElement.eContainer();
						} else { // we are in the root node
							erroneousToken = "ROOT";
						}
					}
				}
			}
			
			logger.error("MSG: " + errm.getMessage() + " - LINE: " + sl + " - TOKEN: '" + erroneousToken + "'");

			// "table-formatted" output
			// System.err.printf("%-50s - %-8s - %-60s %n", "ERROR: " + errm.getMessage(), "LINE: " + sl, "TOKEN: " + erroneousToken);
		}
		return true;
	}
	
	/**
	 * Returns the value corresponding to the passed key
	 * 
	 * @param key
	 *            the name (and the key) of the variable to retrieve
	 * @return the corresponding value if the key is found, null otherwise
	 * @see Map#get(Object)
	 */
	public Object getVariable(String key) {
		return VariablesHelper.getVariable(key);
	}
	
	/**
	 * Puts a key-value pair variable
	 * 
	 * @param key the name of the variable
	 * @param value the value of the variable
	 * @return the previous value associated with key, or null if there was no mapping for key
	 * @see Map#put(Object)
	 */
	public void putVariable(String key, Object value){
		VariablesHelper.putVariable(key, value);
	}
	
	/**
	 * Removes the variable
	 * 
	 * @param key the name of the variable to delete
	 * @return the previous value associated with key, or null if there was no mapping for key
	 * @see Map#remove(Object)
	 */
	public void removeVariable(String key) {
		VariablesHelper.removeVariable(key);
	}
	
	/**
	 * Returns the input
	 * 
	 * @return the input
	 */
	public static DataObject getInput() {
		return input;
	}
	
	/**
	 * Change the level of logger (between ERROR, INFO, DEBUG).
	 * By default it is set to INFO.
	 * 
	 * @param level the name of the needed level to select
	 */
	
	public void setLoggerLevel(String level) {
		switch (level.toUpperCase()) {
		case "ERROR":
			logger.setLevel(Level.ERROR);
			return;
		case "INFO":
			logger.setLevel(Level.INFO);
			return;
		case "DEBUG":
			logger.setLevel(Level.DEBUG);
			return;
		}
	}
	
	/**
	 * Shutdown logger's appender
	 */
	public void shutdownLogger() {
		logger.setLevel(Level.OFF);
	}
	
	
}