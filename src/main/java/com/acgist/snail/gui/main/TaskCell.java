package com.acgist.snail.gui.main;

import com.acgist.snail.gui.Tooltips;
import com.acgist.snail.pojo.ITaskSession;
import com.acgist.snail.pojo.ITaskSession.FileType;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

/**
 * 任务单元格
 * 
 * @author acgist
 * @since 1.0.0
 */
public final class TaskCell extends TableCell<ITaskSession, String> {

	/**
	 * 对齐方式
	 */
	private final Pos pos;
	/**
	 * 是否显示Icon
	 */
	private final boolean icon;
	/**
	 * 是否显示Tooltip
	 */
	private final boolean tooltip;
	
	public TaskCell(Pos pos, boolean icon, boolean tooltip) {
		this.pos = pos;
		this.icon = icon;
		this.tooltip = tooltip;
	}
	
	@Override
	public void updateItem(String value, boolean empty) {
		super.updateItem(value, empty);
		final ITaskSession taskSession = this.getTableRow().getItem();
		if(taskSession != null) {
			final HBox box = new HBox();
			box.setAlignment(this.pos);
			final Text name = new Text(value);
			// 添加图标：文件类型
			if(this.icon) {
//				name.setCursor(Cursor.HAND); // 设置手势
				FileType fileType = taskSession.getFileType();
				if(fileType == null) {
					fileType = FileType.UNKNOWN;
				}
				final ImageView fileTypeIcon = new ImageView("/image/32/" + fileType.icon());
				box.getChildren().add(fileTypeIcon);
			}
			// 添加提示
			if(this.tooltip) {
				this.setTooltip(Tooltips.newTooltip(value));
			}
			box.getChildren().add(name);
			this.setGraphic(box);
		} else {
			final HBox box = new HBox();
			this.setGraphic(box);
		}
	}
	
}
