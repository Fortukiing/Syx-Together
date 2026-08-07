package view.ui.message;

import java.io.Serializable;

import coopmod.CoopRuntime;
import game.time.TIME;
import snake2d.util.gui.renderable.RENDEROBJ;
import view.main.VIEW;

public abstract class Message implements Serializable{

	protected static final int WIDTH = 600;
	protected static final int HEIGHT = 600;
	private static final long serialVersionUID = 1L;
	boolean isRead = false;
	transient RENDEROBJ section;
	double currentSecond = -1;
	private final String title;
	final String key;
	
	public Message(CharSequence title) {
		if (title == null)
			throw new RuntimeException("");
		this.title = ""+title;
		
		String k = "";
		for (StackTraceElement e : new RuntimeException().getStackTrace()) {
			k += e.toString().hashCode(); 
		}
		
		key = k;
	}
	
	protected abstract RENDEROBJ makeSection();
	
	RENDEROBJ section() {
		return section;
	}
	
	public boolean send() {
		currentSecond = TIME.currentSecond();
		CoopRuntime.messageSent(this);
		return VIEW.messages().add(this);
	}
	
	protected final String title() {
		return title;
	}
	
	protected void close() {
		VIEW.messages().hide();
	}
	

	
}
