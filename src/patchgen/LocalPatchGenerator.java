package sharpfix.patchgen;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import edu.brown.cs.ivy.jcomp.JcompSource;
import edu.brown.cs.ivy.jcomp.JcompProject;
import edu.brown.cs.ivy.jcomp.JcompControl;
import edu.brown.cs.ivy.jcomp.JcompMessage;
import sharpfix.util.CodeToken;
import sharpfix.util.CodeTokenGenerator;
import sharpfix.util.ASTNodeReduceChecker0;
import sharpfix.util.ASTNodeReduceChecker1;
import sharpfix.global.*;


public class LocalPatchGenerator
{
    private String bug_id;
    private String sharpfix_dpath;
    private String proj_dpath;
    private String proj_testbuild_dpath;
    private String dependjpath;
    private String output_dpath;
    private String failed_testcases; //Separated by ":"
    private boolean delete_failed_patch;
    //private PatchGenerator0 pgen0;
    private PatchGenerator1 pgen1;
    private PatchValidator pval;
    private CodeTokenGenerator ctg;
    private int parallel_testing;
    private int max_validated_patch;

    public LocalPatchGenerator(RepairInfo ri) {
	bug_id = ri.bugid;
	sharpfix_dpath = ri.sharpfixdpath;
	proj_dpath = ri.projdpath;
	proj_testbuild_dpath = ri.projtestbuilddpath;
	dependjpath = ri.dependjpath;
	output_dpath = ri.outputdpath;
	failed_testcases = ri.failedtestcases;
	delete_failed_patch = ri.deletefailedpatches;
	parallel_testing = ri.paralleltesting;
	max_validated_patch = ri.maxvalidatepatch;
	//pgen0 = new PatchGenerator0();
	pgen1 = new PatchGenerator1();
	//pval = new PatchValidator(bug_id, proj_testbuild_dpath, dependjpath, sharpfix_dpath, output_dpath);
	pval = new PatchValidator(ri);
	ctg = new CodeTokenGenerator();	
    }

    /*NOTE: The candidate chunk should already be !RENAMED! */
    
    public Patch generatePatch(BuggyChunk bchunk, CandidateChunk cchunk, String fix_dpath, Set<String> tested_pset) {
	return generatePatch(bchunk, cchunk, fix_dpath, tested_pset, 2, 1, false, false);
    }

    public Patch generatePatch(BuggyChunk bchunk, CandidateChunk cchunk, String fix_dpath, Set<String> tested_pset, int ccm_flag, int mod_flag) {
	return generatePatch(bchunk, cchunk, fix_dpath, tested_pset, ccm_flag, mod_flag, false, false);
    }

    public Patch generatePatch(BuggyChunk bchunk, CandidateChunk cchunk, String fix_dpath, Set<String> tested_pset, boolean print_ccmatching, boolean print_patches) {
	return generatePatch(bchunk, cchunk, fix_dpath, tested_pset, 2, 1, print_ccmatching, print_patches);
    }    
    
    public Patch generatePatch(BuggyChunk bchunk, CandidateChunk cchunk, String fix_dpath, Set<String> tested_pset, int ccm_flag, int mod_flag, boolean print_ccmatching, boolean print_patches) {

	//Generate all candidate patches
	System.err.println("Generating Initial Patches ...");
	List<Modification> mod_list = pgen1.generatePatches(bchunk, cchunk, ccm_flag, mod_flag, print_ccmatching, print_patches);
	if (mod_list.isEmpty()) { return new Patch(null, false); }
	int mod_list_size = mod_list.size();
	System.err.println("Done Generating "+mod_list_size+" Initial Patches.");

	//Get the buggy method's signature & its matching string (code tokens connected with no space)
	List<ASTNode> bnode_list = bchunk.getNodeList();
	ASTNode bnode0 = bnode_list.get(0);
	ASTNode bnode_tmp = bnode0;
	MethodDeclaration bmd = null;
	String bmd_classname = null;
	String bmd_sig = "";
	String bmd_mstr = "";
	while (bnode_tmp != null) {
	    if (bnode_tmp instanceof MethodDeclaration) { break; }
	    bnode_tmp = bnode_tmp.getParent();
	}
	if (bnode_tmp == null) {
	    System.err.println("Cannot find the buggy method.");
	    return new Patch(null, false);
	}
	bmd = (MethodDeclaration) bnode_tmp;
	bmd_classname = getParentClassName(bmd);
	bmd_sig = getMethodSignatureString(bmd);
	bmd_mstr = getMatchString(bmd);
	
	//Validate the patches
	String bfpath = bchunk.getFilePath();
	File bf = new File(bfpath);
	String bfname = bf.getName();
	String bfctnt = null;
	try { bfctnt = FileUtils.readFileToString(bf, (String)null); }
	catch (Throwable t) { System.err.println(t); t.printStackTrace(); }
	if (bfctnt == null) { return new Patch(null, false); }

	int tested_num = 0;
	if (failed_testcases == null) { //HOW TO IMPROVE?
	    System.err.println("Failed Class Not Available for " + bug_id);
	    return new Patch(null, false);
	}

	//First, sort the generated patches.
	System.err.println("Sorting the Initial Patches ...");
	Collections.sort(mod_list);
	System.err.println("Done sorting the Initial Patches.");

	//Second, generate a list of patch texts to be validated.
	int total_patch = mod_list_size;
	int testable_patch = 0;
	//List<String> patch_status_list = new ArrayList<String>();
	//List<String> patch_text_list = new ArrayList<String>();
	//List<String> pmd_mstr_list = new ArrayList<String>(); //Patch String for identifying redundancy
	List<VPatch> vpatch_list = new ArrayList<VPatch>();

	for (int i=0; i<mod_list_size; i++) {
	    String patch_text = null;
	    Modification mod = mod_list.get(i);
	    ASTRewrite rw = mod.getASTRewrite();
	    Document bdoc = new Document(bfctnt);
	    try {
		rw.rewriteAST(bdoc, null).apply(bdoc);
		patch_text = bdoc.get();
	    } catch (Throwable t) {
		System.err.println("Failed in generating patch for the ASTRewrite:");
		System.err.println(rw.toString());
	    }
	    if (patch_text == null) { continue; }

	    //Resolve the patch using JComp
	    ASTSource patch_astsrc = new ASTSource("", patch_text);
	    List<JcompSource> jcs_list = new ArrayList<JcompSource>();
	    jcs_list.add(patch_astsrc);
	    JcompControl jcc = new JcompControl();
	    JcompProject jcp = jcc.getSemanticData(dependjpath, jcs_list);
	    jcp.resolve();
	    ASTNode patch_root = jcc.getSemanticData(patch_astsrc).getAstNode();

	    MethodFinder mf = new MethodFinder(bmd_classname, bmd_sig);
	    patch_root.accept(mf);
	    MethodDeclaration pmd = mf.getResult();
	    String pmd_mstr = null;
	    if (pmd != null) { pmd_mstr = getMatchString(pmd); }
	    List<JcompMessage> err_msg_list = jcp.getMessages();

	    if (err_msg_list == null || err_msg_list.isEmpty()) {
		if (ASTNodeReduceChecker0.isEqualOrReduced(bmd, pmd) ||
		    ASTNodeReduceChecker1.isReduced(pmd)) {
		    //patch_status_list.add("REDUCED");
		}
		else {
		    if (tested_pset.contains(pmd_mstr) ||
			((pmd_mstr != null) && pmd_mstr.equals(bmd_mstr))) {
			//patch_status_list.add("REDUNDANT");
		    }
		    else {
			//patch_status_list.add("TESTABLE");
			//patch_text_list.add(patch_text);
			//pmd_mstr_list.add(pmd_mstr);
			vpatch_list.add(new VPatch(patch_text, mod.getType(), mod.getHeightSize(), mod.getStringSimilarity()));
			tested_pset.add(pmd_mstr);
			testable_patch += 1;
			if (testable_patch >= max_validated_patch) {
			    break;
			}
		    }
		}
	    }
	    else {
		//String s = "UNRESOLVED:";
		//for (JcompMessage err_msg : err_msg_list) {
		//    s += "\nLine " + err_msg.getLineNumber() + ": " + err_msg.getText();
		//}
		//patch_status_list.add(s);
	    }

	    jcc.freeProject(jcp); //Newly added
	}

	System.err.println("# of Patches Generated: " + total_patch);
	System.err.println("# of Patches to be Validated: " + testable_patch);
	
	//Third, validate each patch.
	Patch rslt_patch0 = validatePatches1(vpatch_list, fix_dpath, bfpath, bfname);
	if (rslt_patch0.isCorrect()) { return rslt_patch0; }
	else {
	    Patch rslt_patch = new Patch(null, false);
	    rslt_patch.setTestedNum(testable_patch);
	    return rslt_patch;
	}
    }

    private Patch validatePatches0(List<VPatch> vpatch_list, String fix_dpath, String bfpath, String bfname) {
	int tested_num = 0;
	int vpatch_list_size = vpatch_list.size();
	for (int i=0; i<vpatch_list_size; i++) {
	    tested_num++;
	    String patch_dpath = fix_dpath+"/p"+i;
	    String patch_fpath = patch_dpath+"/"+bfname;
	    File patch_d = new File(patch_dpath);
	    if (!patch_d.exists()) { patch_d.mkdirs(); }
	    VPatch vpatch = vpatch_list.get(i);
	    Patch patch = pval.validate(vpatch.getText(), patch_fpath, patch_dpath, failed_testcases);
		
	    if (patch.isCorrect()) {
		patch.setTestedNum(tested_num);
		patch.setModificationType(vpatch.getModType());
		patch.setHeightSize(vpatch.getHeightSize());
		patch.setStringSimilarity(vpatch.getStrSimilarity());
		return patch;
	    }
	    else {
		if (delete_failed_patch) {
		    try { FileUtils.deleteDirectory(patch_d); }
		    catch (IOException e) {
			System.err.println(e);
			e.printStackTrace();
		    }
		}
	    }
	}
	return new Patch(null, false, tested_num);
    }

    private Patch validatePatches1(List<VPatch> vpatch_list, String fix_dpath, String bfpath, String bfname) {

	int para_gran = parallel_testing;

	//Init validate runners
	int vpatch_list_size = vpatch_list.size();
	if (vpatch_list_size < para_gran) {
	    Patch patch = validatePatches1Helper(vpatch_list, 0, vpatch_list_size, fix_dpath, bfpath, bfname);
	    patch.setTestedNum(vpatch_list_size);
	    return patch;
	}
	else {
	    for (int i=0; i<vpatch_list_size; ) {
		Patch patch = null;
		if (i+para_gran <= vpatch_list_size) {
		    patch = validatePatches1Helper(vpatch_list, i, i+para_gran, fix_dpath, bfpath, bfname);
		    if (patch.isCorrect()) {
			patch.setTestedNum((i+1)*para_gran);
			return patch;
		    }
		}
		else {
		    patch = validatePatches1Helper(vpatch_list, i, vpatch_list_size, fix_dpath, bfpath, bfname);
		    if (patch.isCorrect()) {
			patch.setTestedNum(vpatch_list_size);
			return patch;
		    }
		}
		i += para_gran;
	    }

	    return new Patch(null, false, vpatch_list_size);
	}
    }

    private Patch validatePatches1Helper(List<VPatch> vpatch_list, int start, int end, String fix_dpath, String bfpath, String bfname) {

	ExecutorService exe_service = Executors.newFixedThreadPool(parallel_testing);
	List<Callable<Patch>> call_list = new ArrayList<Callable<Patch>>();
	for (int i=start; i<end; i++) {
	    String patch_dpath = fix_dpath+"/p"+i;
	    File patch_d = new File(patch_dpath);
	    if (!patch_d.exists()) { patch_d.mkdirs(); }
	    String patch_fpath = patch_dpath+"/"+bfname;
	    ValidatorRunner vrunner = new ValidatorRunner(vpatch_list.get(i), patch_fpath, patch_dpath, failed_testcases);
	    call_list.add(vrunner);
	}

	System.err.println("To validate patches from " + start + " to " + end);
	
	List<Future<Patch>> patch_future_list = null;
	try { patch_future_list = exe_service.invokeAll(call_list); }
	catch (Throwable t) {
	    System.err.println("Patch validating error.");
	    System.err.println(t);
	    t.printStackTrace();
	    exe_service.shutdownNow();
	}
	if (!exe_service.isShutdown()) {
	    exe_service.shutdown();
	}
	
	if (patch_future_list == null) { return new Patch(null, false); }
	for (Future<Patch> patch_future : patch_future_list) {
	    Patch patch = null;
	    try { patch = patch_future.get(); }
	    catch (Throwable t) {
		System.err.println("Error in getting patch from future.");
		System.err.println(t);
		t.printStackTrace();
	    }
	    if (patch != null && patch.isCorrect()) {
		return patch;
	    }
	}

	return new Patch(null, false);
    }
    
    private class ValidatorRunner implements Callable<Patch>
    {
	VPatch vpatch;
	String patch_fpath, patch_dpath, failed_testcases;

	public ValidatorRunner(VPatch vpatch, String patch_fpath, String patch_dpath, String failed_testcases) {
	    this.vpatch = vpatch;
	    this.patch_fpath = patch_fpath;
	    this.patch_dpath = patch_dpath;
	    this.failed_testcases = failed_testcases;
	}

	@Override public Patch call() {
	    String patch_text = vpatch.getText();
	    Patch patch = pval.validate(patch_text, patch_fpath, patch_dpath, failed_testcases);
	    if (!patch.isCorrect() && delete_failed_patch) {
		File patch_f = new File(patch_fpath);
		try { FileUtils.deleteDirectory(patch_f.getParentFile()); }
		catch (Throwable t) {
		    System.err.println(t);
		    t.printStackTrace();
		}
	    }

	    if (patch.isCorrect()) {
		patch.setModificationType(vpatch.getModType());
		patch.setHeightSize(vpatch.getHeightSize());
		patch.setStringSimilarity(vpatch.getStrSimilarity());
	    }

	    return patch;
	}
    }

    private class ASTSource implements JcompSource
    {
	String fpath;
	String ftext;

	public ASTSource(String fpath, String ftext) {
	    this.fpath = fpath;
	    this.ftext = ftext;
	}

	@Override public String getFileContents() { return ftext; }

	@Override public String getFileName() { return fpath; }
    }

    private String getMethodSignatureString(MethodDeclaration md) {
	String mname = md.getName().getIdentifier();
	String marg = null;
	List param_list = md.parameters();
	for (Object param_obj : param_list) {
	    SingleVariableDeclaration param_svd = (SingleVariableDeclaration) param_obj;
	    if (marg == null) { marg = param_svd.getType().toString(); }
	    else { marg += "$" + param_svd.getType().toString(); }
	}
	if (marg == null) { marg = ""; }
	return mname + "(" + marg + ")";
    }

    private class MethodFinder extends ASTVisitor
    {
	private String mclassname;
	private String msig;
	private MethodDeclaration rslt_md;

	public MethodFinder(String mcn, String ms) {
	    mclassname = mcn;
	    msig = ms;
	    rslt_md = null;
	}

	public MethodDeclaration getResult() { return rslt_md; }
	
	@Override public boolean visit(MethodDeclaration md) {
	    String mdsig = getMethodSignatureString(md);
	    if (mdsig.equals(msig)) {
		String mdclassname = getParentClassName(md);
		if(mclassname==null) {
		    if (mdclassname==null) { rslt_md = md; }
		}
		else {
		    if (mclassname.equals(mdclassname)) { rslt_md = md; }
		}
	    }
	    return false;
	}
    }

    private String getMatchString(ASTNode node) {
	List<CodeToken> cts = ctg.getCTs(node, -1);
	StringBuilder sb = new StringBuilder();
	for (CodeToken ct : cts) {
	    sb.append(ct.getText());
	}
	return sb.toString().replaceAll("\\s+","");
    }

    private String getParentClassName(MethodDeclaration md) {
	ASTNode par = md;
	while (par != null) {
	    if (par instanceof AbstractTypeDeclaration) {
		AbstractTypeDeclaration atd = (AbstractTypeDeclaration) par;
		return atd.getName().getIdentifier();
	    }
	    par = par.getParent();
	}
	return null;
    }

    private class VPatch {
	String text;
	String modtype;
	float heightsize;
	float strsim;
	public VPatch(String txt, String mtype, float hsize, float ssim) {
	    text = txt;
	    modtype = mtype;
	    heightsize = hsize;
	    strsim = ssim;
	}
	public String getText() { return text; }
	public String getModType() { return modtype; }
	public float getHeightSize() { return heightsize; }
	public float getStrSimilarity() { return strsim; }
    }
}
