package com.asl.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/landcommitteeevent")
public class CommitteeEventController extends ASLAbstractController {


	@GetMapping
	public String loadCommitteeEventPage() {
		
		
		return "pages/land/landcommitteeevent";
	}
}
