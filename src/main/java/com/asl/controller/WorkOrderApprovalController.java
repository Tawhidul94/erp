package com.asl.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.asl.entity.Cacus;
import com.asl.entity.Caitem;
import com.asl.entity.PoordDetail;
import com.asl.entity.PoordHeader;
import com.asl.entity.ProjectStoreView;
import com.asl.entity.Termstrn;
import com.asl.enums.CodeType;
import com.asl.enums.ResponseStatus;
import com.asl.enums.TransactionCodeType;
import com.asl.model.report.ItemDetails;
import com.asl.model.report.WorkOrderReport;
import com.asl.service.CacusService;
import com.asl.service.CaitemService;
import com.asl.service.ImstockService;
import com.asl.service.PogrnService;
import com.asl.service.PoordService;
import com.asl.service.ProjectStoreViewService;
import com.asl.service.XcodesService;

@Controller
@RequestMapping("/procurement/woapproval")
public class WorkOrderApprovalController extends ASLAbstractController{
	
	@Autowired private PoordService poordService;
	@Autowired private CacusService cacusService;
	@Autowired private CaitemService caitemService;
	@Autowired private PogrnService pogrnService;
	@Autowired private ImstockService imstockService;
	@Autowired private XcodesService xcodeService;

	@GetMapping
	public String loadWorkOrderAppPage(Model model) {
		model.addAttribute("woapproval", getDefaultPoordHeader());
		model.addAttribute("allwoapproval",poordService.getPoordHeadersByXtypetrn(TransactionCodeType.WO_NUMBER.getCode()));
		model.addAttribute("prefix",xtrnService.findByXtypetrn(TransactionCodeType.WO_NUMBER.getCode(), Boolean.TRUE));
		model.addAttribute("warehouses", xcodesService.findByXtype(CodeType.STORE.getCode(), Boolean.TRUE));

		return "pages/procurement/woapproval/woapproval";
	}
	
	private PoordHeader getDefaultPoordHeader() {
		PoordHeader poord = new PoordHeader();
		poord.setXtypetrn(TransactionCodeType.WO_NUMBER.getCode());
		poord.setXstatuspor("Open");
		poord.setXsignatory1(StringUtils.isBlank(sessionManager.getLoggedInUserDetails().getXstaff()) ? "" :sessionManager.getLoggedInUserDetails().getXstaff());
		
		return poord;
	}

	@GetMapping("/{xpornum}")
	public String loadPoordPage(@PathVariable String xpornum, Model model) {
		PoordHeader data = poordService.findPoordHeaderByXpornum(xpornum);
		if (data == null)
			data = getDefaultPoordHeader();
		if (data.getXtotamt() == null)
			data.setXtotamt(BigDecimal.ZERO);

		model.addAttribute("woapproval", data);
		model.addAttribute("allwoapproval",poordService.getPoordHeadersByXtypetrn(TransactionCodeType.WO_NUMBER.getCode()));
		model.addAttribute("prefix",xtrnService.findByXtypetrn(TransactionCodeType.WO_NUMBER.getCode(), Boolean.TRUE));
		model.addAttribute("warehouses", xcodesService.findByXtype(CodeType.STORE.getCode(), Boolean.TRUE));
		model.addAttribute("woapprovaldetailList", poordService.findPoorddetailByXpornum(xpornum));
		model.addAttribute("createdQC", pogrnService.findPogrnHeaderByXpornum(data.getXpornum()));

		List<Termstrn> poorddetails = poordService.findTermstrnByXdocnum(data.getXpornum());
		model.addAttribute("allTermsdef", poorddetails);

		List<PoordDetail> poordDetails = poordService.findPoorddetailByXpornum(xpornum);
		BigDecimal totalQuantityReceived = BigDecimal.ZERO;
		BigDecimal totalLineAmount = BigDecimal.ZERO;
		if (poorddetails != null && !poorddetails.isEmpty()) {
			for (PoordDetail pd : poordDetails) {
				totalQuantityReceived = totalQuantityReceived.add(pd.getXqtypur() == null ? BigDecimal.ZERO : pd.getXqtypur());
				totalLineAmount = totalLineAmount.add(pd.getXlineamt() == null ? BigDecimal.ZERO : pd.getXlineamt());
			}
		}
		model.addAttribute("totalQuantityReceived", totalQuantityReceived);
		model.addAttribute("totalLineAmount", totalLineAmount);
		return "pages/procurement/woapproval/woapproval";
	}
	
	@PostMapping("/save")
	public @ResponseBody Map<String, Object> save(PoordHeader por, BindingResult bindingResult){
		if(por == null) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		por.setXsignatory1(StringUtils.isBlank(sessionManager.getLoggedInUserDetails().getXstaff()) ? "" :sessionManager.getLoggedInUserDetails().getXstaff());

		// Validate
		if(StringUtils.isBlank(sessionManager.getLoggedInUserDetails().getXstaff())) {
			responseHelper.setErrorStatusAndMessage("Authorizer Can't be empty.");
			return responseHelper.getResponse();
		}

		if(StringUtils.isBlank(por.getXsignatorynote1())) {
			responseHelper.setErrorStatusAndMessage("Authorizer Note required");
			return responseHelper.getResponse();
		}

		// If existing
		PoordHeader data = poordService.findPoordHeaderByXpornum(por.getXpornum());
		if(data != null) {
			BeanUtils.copyProperties(por, data, "xtypetrn", "xtrn");
			long count = poordService.update(data);
			if(count == 0) {
				responseHelper.setErrorStatusAndMessage("Can't update Work Order.");
				return responseHelper.getResponse();
			}

			responseHelper.setSuccessStatusAndMessage("Work Order Updated Successfully");
			responseHelper.setRedirectUrl("/procurement/woapproval/" + data.getXpornum());
			return responseHelper.getResponse();
		}

		// If new
		long count = poordService.save(por);
		if(count == 0) {
			responseHelper.setErrorStatusAndMessage("Can't Create Work Order");
			return responseHelper.getResponse();
		}

		responseHelper.setSuccessStatusAndMessage("Work Order Created Successfully");
		responseHelper.setRedirectUrl("/procurement/woapproval/" + por.getXpornum());
		return responseHelper.getResponse();
	}

	@PostMapping("/delete/{xpornum}")
	public @ResponseBody Map<String, Object> delete(@PathVariable String xpornum){
		PoordHeader data = poordService.findPoordHeaderByXpornum(xpornum);
		if(data == null) {
			responseHelper.setErrorStatusAndMessage("Can't delete Work Order");
			return responseHelper.getResponse();
		}

		List<PoordDetail> details = poordService.findPoorddetailByXpornum(xpornum);
		if(details != null && !details.isEmpty()) { 
			responseHelper.setErrorStatusAndMessage("Please delete all Work Order detail first."); 
			return responseHelper.getResponse(); 
		}
		
		responseHelper.setSuccessStatusAndMessage("Deleted Successfully");
		responseHelper.setRedirectUrl("/procurement/woapproval");
		return responseHelper.getResponse();
	}
	
	@PostMapping("/approve/{xpornum}")
	public @ResponseBody Map<String, Object> approve(@PathVariable String xpornum){
		PoordHeader poordHeader = poordService.findPoordHeaderByXpornum(xpornum);
		if(poordHeader == null) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		// Validate
		if(StringUtils.isBlank(poordHeader.getXsignatorynote1())) {
			responseHelper.setErrorStatusAndMessage("Autorizer's Note required");
			return responseHelper.getResponse();
		}
		if(StringUtils.isBlank(sessionManager.getLoggedInUserDetails().getXstaff())) {
			responseHelper.setErrorStatusAndMessage("Autorize By Can't be empty.");
			return responseHelper.getResponse();
		}
		if("Approved".equalsIgnoreCase(poordHeader.getXstatuspor())) {
			responseHelper.setErrorStatusAndMessage("Work Order already approved");
			return responseHelper.getResponse();
		}
		
		List<PoordDetail> details = poordService.findPoorddetailByXpornum(xpornum);
		if(details.isEmpty()){
			responseHelper.setErrorStatusAndMessage("Please add detail!"); 
			return responseHelper.getResponse(); 
		}
		 
		if(poordHeader.getXtotamt().compareTo(BigDecimal.ZERO) == 0.00){
			responseHelper.setErrorStatusAndMessage("Total Amount should not <0.01");
			return responseHelper.getResponse();
		}

		poordHeader.setXstatuspor("Approved");
		long count = poordService.update(poordHeader);
		if(count == 0) {
			responseHelper.setErrorStatusAndMessage("Can't approve Work Order");
			return responseHelper.getResponse();
		}

		responseHelper.setSuccessStatusAndMessage("Work Order approved successfully");
		responseHelper.setRedirectUrl("/procurement/woapproval/" + xpornum);
		return responseHelper.getResponse();
	}

	@PostMapping("/reject/{xpornum}")
	public @ResponseBody Map<String, Object> reject(@PathVariable String xpornum){
		PoordHeader poordHeader = poordService.findPoordHeaderByXpornum(xpornum);
		if(poordHeader == null) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		// Validate
		if(StringUtils.isBlank(poordHeader.getXsignatorynote1())) {
			responseHelper.setErrorStatusAndMessage("Autorizer's Note required");
			return responseHelper.getResponse();
		}
		if(StringUtils.isBlank(sessionManager.getLoggedInUserDetails().getXstaff())) {
			responseHelper.setErrorStatusAndMessage("Autorize By Can't be empty.");
			return responseHelper.getResponse();
		}
		if("Rejected".equalsIgnoreCase(poordHeader.getXstatuspor())) {
			responseHelper.setErrorStatusAndMessage("Work Order already approved");
			return responseHelper.getResponse();
		}
		
		List<PoordDetail> details = poordService.findPoorddetailByXpornum(xpornum);
		if(details.isEmpty()){
			responseHelper.setErrorStatusAndMessage("Please add detail!"); 
			return responseHelper.getResponse(); 
		}
		 
		if(poordHeader.getXtotamt().compareTo(BigDecimal.ZERO) == 0.00){
			responseHelper.setErrorStatusAndMessage("Total Amount should not <0.01");
			return responseHelper.getResponse();
		}

		poordHeader.setXstatuspor("Rejected");
		long count = poordService.update(poordHeader);
		if(count == 0) {
			responseHelper.setErrorStatusAndMessage("Can't reject Work Order");
			return responseHelper.getResponse();
		}

		responseHelper.setSuccessStatusAndMessage("Work Order rejected successfully");
		responseHelper.setRedirectUrl("/procurement/woapproval/" + xpornum);
		return responseHelper.getResponse();
	}
	

	@PostMapping("/createqc/{xpornum}")
	public @ResponseBody Map<String, Object> createqc(@PathVariable String xpornum){
		PoordHeader poordHeader = poordService.findPoordHeaderByXpornum(xpornum);
		if(poordHeader == null) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		// Validate
		if("QC Created".equalsIgnoreCase(poordHeader.getXstatuspor())) {
			responseHelper.setErrorStatusAndMessage("Quality Certificate already created.");
			return responseHelper.getResponse();
		}
		
		String p_seq;
		if(!"Full Received".equalsIgnoreCase(poordHeader.getXstatuspor())) {
			p_seq = xtrnService.generateAndGetXtrnNumber(TransactionCodeType.PROC_ERROR.getCode(), TransactionCodeType.PROC_ERROR.getdefaultCode(), 6);
			poordService.procSP_CREATEGRN_FROMPO(xpornum, "Work Order", p_seq);
			String em = getProcedureErrorMessages(p_seq);
			if(StringUtils.isNotBlank(em)) {
				responseHelper.setErrorStatusAndMessage(em);
				return responseHelper.getResponse();
			}
		}

		responseHelper.setSuccessStatusAndMessage("Quality Certificate created successfully");
		responseHelper.setRedirectUrl("/procurement/woapproval/" + xpornum);
		return responseHelper.getResponse();
	}

	@GetMapping("{xpornum}/woapprovaldetail/{xrow}/show")
	public String openPoreqdetailModal(@PathVariable String xpornum, @PathVariable String xrow, Model model) {
		PoordHeader poordheader = poordService.findPoordHeaderByXpornum(xpornum);
		if(poordheader == null) return "redirect:/procurement/woapproval";

		//model.addAttribute("site", poordheader.getXwh());

		if("new".equalsIgnoreCase(xrow)) {
			PoordDetail poordDetail = new PoordDetail();
			poordDetail.setXpornum(xpornum);
			poordDetail.setXqtypur(BigDecimal.ONE.setScale(2, RoundingMode.DOWN));
			poordDetail.setXrate(BigDecimal.ZERO.setScale(2, RoundingMode.DOWN));
			poordDetail.setXlineamt(BigDecimal.ZERO.setScale(2, RoundingMode.DOWN));
			model.addAttribute("woapprovaldetail", poordDetail);
			model.addAttribute("availablestock", Collections.emptyList());
			model.addAttribute("purpose", xcodeService.findByXtype(CodeType.EXPENSE_PURPOSE.getCode(), Boolean.TRUE));
		} else {
			PoordDetail poordDetail = poordService.findPoorddetailByXpornumAndXrow(xpornum, Integer.parseInt(xrow));
			if(poordDetail == null) {
				poordDetail = new PoordDetail();
				poordDetail.setXpornum(xpornum);
				poordDetail.setXqtypur(BigDecimal.ONE.setScale(2, RoundingMode.DOWN));
				poordDetail.setXrate(BigDecimal.ZERO.setScale(2, RoundingMode.DOWN));
				poordDetail.setXlineamt(BigDecimal.ZERO.setScale(2, RoundingMode.DOWN));
			}
			model.addAttribute("woapprovaldetail", poordDetail);
			model.addAttribute("availablestock", imstockService.findByXitem(poordDetail.getXitem()));
			model.addAttribute("purpose", xcodeService.findByXtype(CodeType.EXPENSE_PURPOSE.getCode(), Boolean.TRUE));
		}
		return "pages/procurement/woapproval/woapprovaldetailmodal::woapprovaldetailmodal";
	}
	@PostMapping("/woapprovaldetail/save")
	public @ResponseBody Map<String, Object> savePoorddetail(PoordDetail poorddetail){
		if(poorddetail == null || StringUtils.isBlank(poorddetail.getXpornum()) || StringUtils.isBlank(poorddetail.getXitem())) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		if(StringUtils.isBlank(poorddetail.getXitem())) {
			responseHelper.setErrorStatusAndMessage("Please select an item");
			return responseHelper.getResponse();
		}
		if(poorddetail.getXrate().compareTo(BigDecimal.ZERO) == 0.00 ) {
			responseHelper.setErrorStatusAndMessage("Unit Price should not <0.01");
			return responseHelper.getResponse();
		}
		
		if(poorddetail.getXqtypur().compareTo(BigDecimal.ZERO) == 0.00){
			responseHelper.setErrorStatusAndMessage("Quantity should not <0.01");
			return responseHelper.getResponse();
		}
		// Check item already exist in detail list
		if(poorddetail.getXrow() == 0 && poordService.findPoorddetailByXpornumAndXitem(poorddetail.getXpornum(), poorddetail.getXitem()) != null) {
			responseHelper.setErrorStatusAndMessage("Item already added into detail list. Please add another one or update existing");
			return responseHelper.getResponse();
		}
		
		// if existing
		PoordDetail existDetail = poordService.findPoorddetailByXpornumAndXrow(poorddetail.getXpornum(), poorddetail.getXrow());
		if(existDetail != null) {
			
			BeanUtils.copyProperties(poorddetail, existDetail, "xpornum", "xrow");
			long count = poordService.updateDetail(existDetail);
			if(count == 0) {
				responseHelper.setStatus(ResponseStatus.ERROR);
				return responseHelper.getResponse();
			}

			responseHelper.setReloadSectionIdWithUrl("woapprovaldetailtable", "/procurement/woapproval/woapprovaldetail/" + poorddetail.getXpornum());
			responseHelper.setSuccessStatusAndMessage("Order detail updated successfully");
			return responseHelper.getResponse();
		}

		// if new detail
		long count = poordService.saveDetail(poorddetail);
		if(count == 0) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		responseHelper.setReloadSectionIdWithUrl("woapprovaldetailtable", "/procurement/woapproval/woapprovaldetail/" + poorddetail.getXpornum());
		responseHelper.setSuccessStatusAndMessage("Order detail saved successfully");
		return responseHelper.getResponse();
	}

	@GetMapping("/woapprovaldetail/{xpornum}")
	public String reloadPoreqdetailTabble(@PathVariable String xpornum, Model model) {
		List<PoordDetail> poorddetails = poordService.findPoorddetailByXpornum(xpornum);
		model.addAttribute("woapprovaldetailList", poorddetails);
		model.addAttribute("woapproval", poordService.findPoordHeaderByXpornum(xpornum));
		
		BigDecimal totalQuantityReceived = BigDecimal.ZERO;
		BigDecimal totalLineAmount = BigDecimal.ZERO;
		if (poorddetails != null && !poorddetails.isEmpty()) {
			for (PoordDetail pd : poorddetails) {
				totalQuantityReceived = totalQuantityReceived.add(pd.getXqtypur() == null ? BigDecimal.ZERO : pd.getXqtypur());
				totalLineAmount = totalLineAmount.add(pd.getXlineamt() == null ? BigDecimal.ZERO : pd.getXlineamt());
			}
		}
		model.addAttribute("totalQuantityReceived", totalQuantityReceived);
		model.addAttribute("totalLineAmount", totalLineAmount);
		
		return "pages/procurement/woapproval/woapproval::woapprovaldetailtable";
	}

	@PostMapping("{xpornum}/woapprovaldetail/{xrow}/delete")
	public @ResponseBody Map<String, Object> deleteporeqdetail(@PathVariable String xpornum, @PathVariable String xrow, Model model) {
		PoordDetail detail = poordService.findPoorddetailByXpornumAndXrow(xpornum, Integer.parseInt(xrow));
		if(detail == null) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		long count = poordService.deleteDetail(detail);
		if(count == 0) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}

		responseHelper.setSuccessStatusAndMessage("Deleted successfully");
		responseHelper.setReloadSectionIdWithUrl("woapprovaldetailtable", "/procurement/woapproval/woapprovaldetail/" + xpornum);
		return responseHelper.getResponse();
	}
	
	@GetMapping("/itemdetail/{xitem}")
	public @ResponseBody Caitem getItemDetail(@PathVariable String xitem){
		return caitemService.findByXitem(xitem);
	}


	@GetMapping("/print/{xpornum}")
	public ResponseEntity<byte[]> printPoreqnumHeaderWithDetails(@PathVariable String xpornum, HttpServletRequest request) {
		String message;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("text", "html"));
		headers.add("X-Content-Type-Options", "nosniff");

		PoordHeader data = poordService.findPoordHeaderByXpornum(xpornum);
		SimpleDateFormat sdf = new SimpleDateFormat("E, dd-MMM-yyyy, HH:mm:ss");
		Cacus cacus = cacusService.findByXcus(data.getXcus());
		WorkOrderReport report=new WorkOrderReport();

		report.setBusinessName(sessionManager.getZbusiness().getZorg());
		report.setBusinessAddress(sessionManager.getZbusiness().getXmadd());
		report.setReportName("Work Order");
		report.setReportLogo(sessionManager.getZbusiness().getXbimage());
		if(data.getXdate()==null) {
			report.setFromDate("");
		}
		else if(data.getXdate()!=null) {
			report.setFromDate(sdf.format(data.getXdate()));
		}
		report.setPrintDate(new SimpleDateFormat("E, dd-MMM-yyyy HH:mm:ss").format(new Date()));
		report.setPhone(sessionManager.getZbusiness().getXphone());
		report.setFax(sessionManager.getZbusiness().getXfax());
		report.setXpornum(data.getXpornum());
		if(data.getXdate()==null) {
			report.setXdate("");
		}
		else if(data.getXdate()!=null) {
			report.setXdate(sdf.format(data.getXdate()));
		}
		report.setXcus(data.getXcus());
		report.setCusName(cacus.getXorg());
		report.setXwh(data.getXwh());
		report.setStoreName(xcodesService.findByXtypesAndXcodes(CodeType.STORE.getCode(), data.getXwh(), Boolean.TRUE).getXlong());
		report.setXquotnum(data.getXquotnum());
		report.setXref(data.getXref());
		report.setXpreparer(data.getXpreparer());
		report.setPreparerName(data.getXpreparername());
		report.setXnote(data.getXnote());
		report.setXsignatory1(data.getXsignatory1());
		report.setSignatoryName(data.getXsignatoryname());
		report.setXsignatorynote1(data.getXsignatorynote1());
		report.setXstatuspor(data.getXstatuspor());
		report.setXstatus(data.getXstatus());
		report.setXtotamt(data.getXtotamt());

		if(data.getXprepdate()==null) {
			report.setXprepdate("");
		}
		else if(data.getXprepdate()!=null) {
			report.setXprepdate(sdf.format(data.getXprepdate()));
		}

		if(data.getXsignatorydate1()==null) {
			report.setXsignatorydate1("");
		}
		if(data.getXsignatorydate1()!=null)
		{
			report.setXsignatorydate1(sdf.format(data.getXsignatorydate1()));
		}

		if("Open".equalsIgnoreCase(data.getXstatus())) {
			report.setXstatus("Open");
		}
		else if("Confirmed".equalsIgnoreCase(data.getXstatus())) {
			report.setXstatus("Applied");
		}

		if("Approved".equalsIgnoreCase(data.getXstatuspor()))
		{
			report.setXstatuspor("Approved");
		}
		else if(("Rejected".equalsIgnoreCase(data.getXstatuspor())))
		{
			report.setXstatuspor("Rejected");
		}

		List<PoordDetail> items = poordService.findPoorddetailByXpornum(data.getXpornum());
		if (items != null && !items.isEmpty()) {
			items.stream().forEach(it -> {
				ItemDetails item = new ItemDetails();
				item.setItemCode(it.getXitem());
				item.setItemName(it.getItemname());
				item.setItemQty(it.getXqtypur().toString());
				item.setRate(it.getXrate());
				item.setItemUnit(it.getXunitpur());
				item.setItemQty(it.getXqtypur() != null ? it.getXqtypur().toString() : BigDecimal.ZERO.toString());
				item.setLineamt(it.getXlineamt());
				item.setPurpose(it.getXpurpose());
				report.getItems().add(item);
			});
		}
		
		List<Termstrn> terms = poordService.findTermstrnByXdocnum(xpornum);
		if(terms != null && !terms.isEmpty()) report.setDfterms(terms);

		byte[] byt = getPDFByte(report, "workorderreport.xsl", request);
		if(byt == null) {
			message = "Can't generate pdf for this Work Order: " + xpornum;
			return new ResponseEntity<>(message.getBytes(), headers, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		headers.setContentType(new MediaType("application", "pdf"));
		return new ResponseEntity<>(byt, headers, HttpStatus.OK);
	}

}
