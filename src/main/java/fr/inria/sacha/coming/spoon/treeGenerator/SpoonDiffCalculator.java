package fr.inria.sacha.coming.spoon.treeGenerator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import spoon.compiler.SpoonCompiler;
import spoon.compiler.SpoonResource;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.FactoryImpl;
import spoon.support.DefaultCoreFactory;
import spoon.support.StandardEnvironment;
import spoon.support.compiler.SnippetCompilationError;
import spoon.support.compiler.VirtualFile;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;
import spoon.support.compiler.jdt.JDTSnippetCompiler;
import fr.inria.sacha.coming.analyzer.ActionClassifier;
import fr.inria.sacha.coming.analyzer.DiffResult;
import fr.labri.gumtree.actions.ActionGenerator;
import fr.labri.gumtree.actions.model.Action;
import fr.labri.gumtree.matchers.CompositeMatchers;
import fr.labri.gumtree.matchers.Mapping;
import fr.labri.gumtree.matchers.MappingStore;
import fr.labri.gumtree.matchers.Matcher;
import fr.labri.gumtree.matchers.MatcherFactory;
import fr.labri.gumtree.tree.Tree;
import fr.labri.gumtree.tree.TreeUtils;

/**
 * Computes the differences between two CtElements.
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class SpoonDiffCalculator {

	public static final Logger logger = Logger.getLogger(SpoonDiffCalculator.class);
	protected Factory factory = null;

	protected Set<Mapping> mappings = null;
	protected MappingStore mappingsComp = null;
	
	protected boolean decorateTree = false;
	
	public SpoonDiffCalculator(boolean noClasspath) {
		this();
		factory.getEnvironment().setNoClasspath(noClasspath);
	}
	
	public SpoonDiffCalculator(boolean noClasspath,boolean decorate ) {
		this();
		factory.getEnvironment().setNoClasspath(noClasspath);
		this.decorateTree = decorate;
	}
	
	
	public SpoonDiffCalculator(Factory factory, boolean decorate) {
		this(factory);
		this.decorateTree = decorate;
	}
	
	public SpoonDiffCalculator(Factory factory) {
		this.factory = factory;
		logger.setLevel(Level.DEBUG);
		factory.getEnvironment().setNoClasspath(true);
	}

	public SpoonDiffCalculator() {
		factory = new FactoryImpl(new DefaultCoreFactory(),
				new StandardEnvironment());
		logger.setLevel(Level.DEBUG);
		factory.getEnvironment().setNoClasspath(true);
	}

	

	@Deprecated
	public DiffResult compare(String left, String right) {

		CtType<?> clazz1;
		try {
			clazz1 = getCtType(left);
	

		CtType<?> clazz2 = getCtType(right);
		//factory.Code().createCodeSnippetStatement(right)
		//		.compile();

			
		return analyze(clazz1, clazz2);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
			
	}
	
	public CtClass getCtClass(File f) throws Exception{
		SpoonResource sr1 = SpoonResourceHelper .createResource(f) ;
		SpoonCompiler compiler = new JDTBasedSpoonCompiler(factory);
		compiler.addInputSource(sr1);
		compiler.build();
		CtClass<?> clazz1 = (CtClass<?>) factory.Type().getAll().get(0);
		return clazz1;
	}

	
	public  CtType<?> getCtType(String content) throws Exception{
				
		SpoonCompiler compiler = new JDTSnippetCompiler(factory, content);//new JDTBasedSpoonCompiler(factory);
		//compiler.addInputSource(new VirtualFile(content,""));
		compiler.build();
		CtClass<?> clazz1 = (CtClass<?>) factory.Type().getAll().get(0);
		return clazz1;
	}
	
	public  CtType<?> getCtType2(String content) throws Exception{
		
/*	factory.Package().getAllRoots().clear();
	factory.Type().getAll().clear();*/
	SpoonCompiler builder = new JDTSnippetCompiler(factory, content);

	builder.addInputSource(new VirtualFile(content,""));
	
		try {
			builder.build();
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		CtType<?> ret =  factory.Type().getAll().get(0);
		return ret;
	}
	
	public DiffResult compare(URL f1, URL f2) throws Exception {
		return this.compare(new File(f1.getFile()), new File(f1.getFile()));
	}
	
	public DiffResult compare(File f1, File f2) throws Exception {
		 
		CtClass<?> clazz1 = getCtClass(f1);
			
		CtClass<?> clazz2 = getCtClass(f2);
		
		DiffResult result = this.analyze(clazz1,clazz2);
		
		return result;
	}


		
	
	public Tree getTree(CtElement element){
		SpoonGumTreeBuilder scanner = new SpoonGumTreeBuilder(this.decorateTree );

		scanner.scan(element);
		Tree tree = scanner.getRoot();
		prepare(tree);
		
		scanner.root = null;
		scanner.nodes.clear();
		return tree;
	}

	public DiffResult analyze(CtElement left, CtElement right) {

		Tree rootSpoonLeft = getTree(left);

		Tree rootSpoonRight = getTree(right);

		return compare(rootSpoonLeft, rootSpoonRight);
	}

	public DiffResult compare(Tree rootSpoonLeft, Tree rootSpoonRight) {
	
		List<Action> actions = null;

	//	GumTreeMatcher.prepare(rootSpoonLeft);
	//	GumTreeMatcher.prepare(rootSpoonRight);
		

		
		prepare(rootSpoonLeft);
		prepare(rootSpoonRight);
		
		//---
		/*logger.debug("-----Trees:----");
		logger.debug("left tree:  " + rootSpoonLeft.toTreeString());
		logger.debug("right tree: " + rootSpoonRight.toTreeString());
*/
		// --
		//Matcher matcher = new GumTreeMatcher(rootSpoonLeft, rootSpoonRight);
		MatcherFactory f = new CompositeMatchers.GumTreeMatcherFactory();
		Matcher matcher = f.newMatcher(rootSpoonLeft, rootSpoonRight);
		
		//new 
		matcher.match();
		//
		mappings = matcher.getMappingSet();
		mappingsComp = new MappingStore(mappings);

		ActionGenerator gt = new ActionGenerator(rootSpoonLeft, rootSpoonRight,	matcher.getMappings());
		gt.generate();
		actions = DiffResult.getAllFilterDuplicate(gt.getActions());//gt.getActions();

		ActionClassifier gtfac = new ActionClassifier();
		
		List<Action> rootActions = gtfac.getRootActions(mappings, actions);
		
		return new DiffResult(actions, rootActions);
	}

	/**
	 * 
	 * @param rootActions
	 * @param actionParent
	 * @return
	 */
	public List<Action> retriveActionChilds(List<Action> rootActions,
			Action actionParent) {

		List<Action> actions = new ArrayList<Action>();

		for (Action action : rootActions) {
			Tree t = action.getNode();
			if (t.getParent().equals(actionParent)) {
				actions.add(action);
			}

		}

		return rootActions;
	}

	public void getCtClass(Factory factory, String contents) {
		SpoonCompiler builder = new JDTSnippetCompiler(factory, contents);
		builder.build();
		
	}
	
	protected boolean problemVersion(SnippetCompilationError e){
		for(String prob : e.problems){
			if(prob.startsWith("Syntax error on token \"enum\"")){
				return true;
			}
		}
			return false;
	}
	
	public CtType getSpoonType(String contents, int compliance) throws IllegalStateException,SnippetCompilationError {
		this.factory.getEnvironment().setComplianceLevel(compliance);
		return getSpoonType(contents);
	}
		
	public CtType getSpoonType(String contents) throws IllegalStateException,SnippetCompilationError {
		Exception exe = null;
		try {
			this.getCtClass(factory, contents);
		} catch (SnippetCompilationError e) {
			exe = e;
			//System.out.println(e.problems);
			boolean error = problemVersion(e);
			if(error){
				throw e;
			}
			
		}
		List<CtType<?>> types = factory.Type().getAll();
		if(types.isEmpty())
		{
			throw new IllegalStateException("No Type was created by spoon");
		}
		CtType spt = types.get(0);
		spt.getPackage().getTypes().remove(spt);
		//System.out.println("Retreving "+spt.getQualifiedName());
		return spt;

	}

	public String printTree(String tab, Tree t) {

		StringBuffer b = new StringBuffer();
		b.append(t.getTypeLabel() + ":" + t.getLabel() + " \n");
		Iterator<Tree> cIt = t.getChildren().iterator();
		while (cIt.hasNext()) {
			Tree c = cIt.next();
			b.append(tab + " " + printTree("\t" + tab, c));
			// if (cIt.hasNext()) b.append(" ");
		}
		// b.append(")");
		return b.toString();

	}
	
	public void prepare(Tree node){
		node.refresh();
		TreeUtils.postOrderNumbering(node);
		TreeUtils.computeHeight(node);
		TreeUtils.computeDigest(node);
	}
	
	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.out.println("Usage: DiffSpoon <file_1>  <file_2>");
			return;
		}

		File f1 = new File(args[0]);
		File f2 = new File(args[1]);

	

		SpoonDiffCalculator ds = new SpoonDiffCalculator(true);
		DiffResult result = ds.compare(f1, f2);

	}

	public static String readFile(File f) throws IOException {
		FileReader reader = new FileReader(f);
		char[] chars = new char[(int) f.length()];
		reader.read(chars);
		String content = new String(chars);
		reader.close();
		return content;
	}

}
