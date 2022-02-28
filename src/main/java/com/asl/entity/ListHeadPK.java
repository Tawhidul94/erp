package com.asl.entity;

import java.io.Serializable;

import lombok.Data;

/**
 * @author Zubayer Ahamed
 * @since Jul 22, 2021
 */
@Data
public class ListHeadPK implements Serializable {

	private static final long serialVersionUID = -7418630667004330245L;

	private String zid;
	private String listcode;
}
