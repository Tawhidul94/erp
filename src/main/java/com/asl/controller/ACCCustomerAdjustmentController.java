package com.asl.controller;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.asl.entity.Acdetail;
import com.asl.entity.Acsubview;
import com.asl.entity.Arhed;
import com.asl.entity.Cacus;
import com.asl.enums.CodeType;
import com.asl.enums.ResponseStatus;
import com.asl.enums.TransactionCodeType;
import com.asl.service.AcService;
import com.asl.service.AcsubviewSerevice;
import com.asl.service.ArhedService;
import com.asl.service.CacusService;
import com.asl.service.XcodesService;

@Controller
@RequestMapping("/cusadjustment")
public class ACCCustomerAdjustmentController extends ASLAbstractController{

	@Autowired private ArhedService arhedService;
	@Autowired private XcodesService xcodeService;	
	@Autowired private AcService acService;
	@Autowired private AcsubviewSerevice acsubviewSerevice;
	@Autowired private CacusService cacusService;

	@GetMapping
	public String loadCusAdjustmentPage(Model model) {

		model.addAttribute("arhed", getDefaultArhed());
		model.addAttribute("arhedprefix", xtrnService.findByXtypetrnAndXtrn(TransactionCodeType.ACCOUNT_ADAR.getCode(),TransactionCodeType.ACCOUNT_ADAR.getdefaultCode(), Boolean.TRUE));
		model.addAttribute("adjustmenttypeList", xcodeService.findByXtype(CodeType.ADJUSTMENT_TYPE.getCode(),Boolean.TRUE));
		model.addAttribute("adjustmentwithList", xcodeService.findByXtype(CodeType.ADJUSTMENT_WITH.getCode(),Boolean.TRUE));
		model.addAttribute("allArhed", arhedService.getAllArhedByXtype(TransactionCodeType.ACCOUNT_ADAR.getdefaultCode()));
		model.addAttribute("purpose", xcodeService.findByXtype(CodeType.PURPOSE.getCode(), Boolean.TRUE));
		model.addAttribute("subaccounts", Collections.emptyList());
		return "pages/salesninvoice/customeradjustment/cusadjustment";
	}

	@GetMapping("/{xvoucher}")
	public String loadCusAdjusmentPage(@PathVariable String xvoucher, Model model) {
		Arhed data = arhedService.findArhedByXvoucher(xvoucher);
		if(data == null) data = getDefaultArhed();

		
		data.setXtypetrn(TransactionCodeType.ACCOUNT_ADAR.getCode());
		model.addAttribute("arhed", data);
		model.addAttribute("arhedprefix", xtrnService.findByXtypetrn(TransactionCodeType.ACCOUNT_ADAR.getCode()));
		model.addAttribute("adjustmenttypeList", xcodeService.findByXtype(CodeType.ADJUSTMENT_TYPE.getCode()));
		model.addAttribute("adjustmentwithList", xcodeService.findByXtype(CodeType.ADJUSTMENT_WITH.getCode()));
		model.addAttribute("allArhed", arhedService.getAllArhedByXtype(TransactionCodeType.ACCOUNT_ADAR.getdefaultCode()));
		model.addAttribute("purpose", xcodeService.findByXtype(CodeType.PURPOSE.getCode(), Boolean.TRUE));
		
		List<Acsubview> list = acsubviewSerevice.findSubAccountByXbank(data.getXbank());
		list.sort(Comparator.comparing(Acsubview::getXsub));
		model.addAttribute("subaccounts", list);
		
		List<Acdetail> acdetails = acService.findAcdetailsByXvoucher(data.getXdocnum());
		BigDecimal totalDebit = BigDecimal.ZERO;
		BigDecimal totalCredit = BigDecimal.ZERO;
		if(acdetails != null && !acdetails.isEmpty()) {
			for(Acdetail acd : acdetails) {
				totalDebit = totalDebit.add(acd.getXdebit() == null ? BigDecimal.ZERO : acd.getXdebit());
				totalCredit = totalCredit.add(acd.getXcredit() == null ? BigDecimal.ZERO : acd.getXcredit());
			}
		}
		model.addAttribute("acdetails", acdetails);
		model.addAttribute("totalDebit", totalDebit);
		model.addAttribute("totalCredit", totalCredit);

		return "pages/salesninvoice/customeradjustment/cusadjustment";
	}

	private Arhed getDefaultArhed() {
		Arhed arhed = new Arhed();

		arhed.setXtypeadj("AP");
		arhed.setXtrntype(TransactionCodeType.ACCOUNT_ADAR.getCode());
		arhed.setXdate(new Date());
		arhed.setXprime(BigDecimal.ZERO);
		arhed.setXstatus("Open");
		arhed.setXtrn("JV--");
		

		return arhed;
	}

	@PostMapping("/save")
	public @ResponseBody Map<String, Object> save(Arhed arhed, BindingResult bindingResult){
		// Validate
		if(StringUtils.isBlank(arhed.getXcus())) {
			responseHelper.setErrorStatusAndMessage("Customer required");
			return responseHelper.getResponse();
		}
		Cacus cacus = cacusService.findByXcus(arhed.getXcus());
		
		if((cacus.getXgcus().equalsIgnoreCase("Member"))) {
			if(StringUtils.isBlank(arhed.getXland())) {
				responseHelper.setErrorStatusAndMessage("Land required");
				return responseHelper.getResponse();
			}
		}
		/*
		 * if(StringUtils.isBlank(arhed.getXbank())) {
		 * responseHelper.setErrorStatusAndMessage("Adjustment Account required");
		 * return responseHelper.getResponse(); }
		 */
		
		if(arhed.getXprime().compareTo(BigDecimal.ZERO) == -1 || arhed.getXprime().equals(BigDecimal.ZERO)) {
			responseHelper.setErrorStatusAndMessage("Invalid amount");
			return responseHelper.getResponse();
		}

		//Modify transaction codes for arhed
		arhed.setXstatusjv("Open");
		arhed.setXtypetrn("Sale");
		arhed.setXpaymentterm("Credit");
		arhed.setXbase(arhed.getXprime());
		arhed.setXpaymenttype("Cash");
		//arhed.setXwh("01");
		arhed.setXtrn("JV--");
		arhed.setXcur("BDT");
		if (arhed.getXtypeobj().equals("Debit")){
			arhed.setXsign(1);
		}
		else {arhed.setXsign(-1);}
		
		// if existing record
		if(StringUtils.isNotBlank(arhed.getXvoucher())) {
			Arhed existArhed = arhedService.findArhedByXvoucher(arhed.getXvoucher());
			BeanUtils.copyProperties(arhed, existArhed, "xvoucher", "xtype");
			long count = arhedService.update(existArhed);
			if(count == 0) {
				responseHelper.setStatus(ResponseStatus.ERROR);
				return responseHelper.getResponse();
			}
			
			
			responseHelper.setSuccessStatusAndMessage("Customer Adjustment updated successfully");
			responseHelper.setRedirectUrl("/cusadjustment/" + arhed.getXvoucher());
			return responseHelper.getResponse();
			
			
		}

		// If new
		long count = arhedService.save(arhed);
		if(count == 0) {
			responseHelper.setStatus(ResponseStatus.ERROR);
			return responseHelper.getResponse();
		}
		responseHelper.setSuccessStatusAndMessage("Customer Adjustment created successfully");
		responseHelper.setRedirectUrl("/cusadjustment/" + arhed.getXvoucher());
		return responseHelper.getResponse();
	}

	
	@PostMapping("/delete/{xvoucher}")
	public @ResponseBody Map<String, Object> delete(@PathVariable String xvoucher, Model model){
		Arhed voucher = arhedService.findArhedByXvoucher(xvoucher);
		if(voucher == null) {
			responseHelper.setErrorStatusAndMessage("Customer Adjustment not found in this system");
			return responseHelper.getResponse();
		}

		long count = arhedService.deleteVoucher(xvoucher);
		if(count == 0) {
			responseHelper.setErrorStatusAndMessage("Can't delete Customer Adjustment");
			return responseHelper.getResponse();
		}

		responseHelper.setSuccessStatusAndMessage("Customer Adjustment deleted successfully");
		responseHelper.setRedirectUrl("/cusadjustment");
		return responseHelper.getResponse();
	}
	
	@PostMapping("/confirm/{xvoucher}")
	public @ResponseBody Map<String, Object> confirmMoneyReceipt(@PathVariable String xvoucher){
		Arhed voucher = arhedService.findArhedByXvoucher(xvoucher);
		if(voucher == null) {
			responseHelper.setErrorStatusAndMessage("Customer Adjustment not found in this system");
			return responseHelper.getResponse();
		}

		voucher.setXstatus("Confirmed");
		long count = arhedService.update(voucher);
		if(count == 0) {
			responseHelper.setErrorStatusAndMessage("Can't confirm Customer Adjustment");
			return responseHelper.getResponse();
		}

		responseHelper.setSuccessStatusAndMessage("Customer Adjustment confirmed successfully");
		responseHelper.setRedirectUrl("/cusadjustment/" + voucher.getXvoucher());
		return responseHelper.getResponse();
	}
	
	@PostMapping("/transfertogl/{xvoucher}")
	public @ResponseBody Map<String, Object> transferToGL(@PathVariable String xvoucher){
		
		Arhed arhed = arhedService.findArhedByXvoucher(xvoucher);
		if(arhed == null) {
			responseHelper.setErrorStatusAndMessage("Adjustment is not found in this system");
			return responseHelper.getResponse();
		}
		//Problem in Bank Header
		// Validate
		
		
		/*
		 * if(!"Open".equalsIgnoreCase(arhed.getXstatusjv())) {
		 * responseHelper.setErrorStatusAndMessage("Voucher Already Created"); return
		 * responseHelper.getResponse(); }
		 */
		
		String p_seq;
		if(!"Confirmed".equalsIgnoreCase(arhed.getXstatusjv())) {
			p_seq = xtrnService.generateAndGetXtrnNumber(TransactionCodeType.PROC_ERROR.getCode(), TransactionCodeType.PROC_ERROR.getdefaultCode(), 6);
			arhedService.procTransferARAP_Adjustment(xvoucher,arhed.getXtrn(), p_seq);
			String em = getProcedureErrorMessages(p_seq);
			if(StringUtils.isNotBlank(em)) {
				responseHelper.setErrorStatusAndMessage(em);
				return responseHelper.getResponse();
			}
		}
		responseHelper.setSuccessStatusAndMessage("Transferred to GL successfully");
		responseHelper.setRedirectUrl("/cusadjustment/" + xvoucher);
		return responseHelper.getResponse();
	}

}
