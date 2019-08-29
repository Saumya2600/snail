package com.acgist.snail.gui;

import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * <p>提示框</p>
 * 
 * @author acgist
 * @since 1.1.0
 */
public class Tooltips {

	/**
	 * 新建提示框，默认显示时间：200（毫秒）
	 * 
	 * @param value 提示内容
	 * 
	 * @return 提示框
	 */
	public static final Tooltip newTooltip(String value) {
		return newTooltip(value, 200);
	}
	
	/**
	 * 新建提示框
	 * 
	 * @param value 提示内容
	 * @param millis 显示时间（鼠标移到目标）
	 * 
	 * @return 提示框
	 */
	public static final Tooltip newTooltip(String value, int millis) {
		final Tooltip tooltip = new Tooltip(value);
		tooltip.setShowDelay(Duration.millis(millis));
		return tooltip;
	}
	
}
