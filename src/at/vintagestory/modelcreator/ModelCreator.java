package at.vintagestory.modelcreator;

import static org.lwjgl.opengl.GL11.glViewport;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import at.vintagestory.modelcreator.gui.GuiMain;
import at.vintagestory.modelcreator.gui.Icons;
import at.vintagestory.modelcreator.gui.left.LeftKeyFramesPanel;
import at.vintagestory.modelcreator.gui.left.LeftSidebar;
import at.vintagestory.modelcreator.gui.left.LeftUVSidebar;
import at.vintagestory.modelcreator.gui.middle.ModelRenderer;
import at.vintagestory.modelcreator.gui.right.RightTopPanel;
import at.vintagestory.modelcreator.interfaces.IDrawable;
import at.vintagestory.modelcreator.interfaces.IElementManager;
import at.vintagestory.modelcreator.interfaces.ITextureCallback;
import at.vintagestory.modelcreator.model.Element;
import at.vintagestory.modelcreator.model.PendingTexture;
import at.vintagestory.modelcreator.util.screenshot.PendingScreenshot;
import at.vintagestory.modelcreator.util.screenshot.Screenshot;

import java.util.prefs.Preferences;

public class ModelCreator extends JFrame
{
	public static String windowTitle = "Vintage Story Model Creator"; 
	private static final long serialVersionUID = 1L;
	
	public static ModelCreator Instance;
	
	public static Preferences prefs;
	
	public static Project currentProject;
	public static boolean updatingValues = false;
	public static boolean projectWasModified;

	
	public static boolean transparent = true;
	public static boolean unlockAngles = false;

	// Canvas Variables
	private final static AtomicReference<Dimension> newCanvasSize = new AtomicReference<Dimension>();
	private final Canvas canvas;
	private int width = 990, height = 700;

	// Swing Components
	private JScrollPane scroll;
	public static IElementManager manager;
	private Element grabbed = null;

	// Texture Loading Cache
	public List<PendingTexture> pendingTextures = new ArrayList<PendingTexture>();
	private PendingScreenshot screenshot = null;

	private int lastMouseX, lastMouseY;
	private boolean grabbing = false;
	private boolean closeRequested = false;

	/* Sidebar Variables */
	private final int SIDEBAR_WIDTH = 130;
	
	public LeftSidebar uvSidebar;
	
	public static LeftKeyFramesPanel leftKeyframesPanel;

	
	public ModelRenderer modelrenderer;
	
	public long prevFrameMillisec;
	
	
	static {
		prefs = Preferences.systemNodeForPackage(ModelCreator.class);
	}
	
	
	public ModelCreator(String title)
	{
		super(title);
		Instance = this;
		
		currentProject = new Project(null);
		
		setDropTarget(getCustomDropTarget());		
		setPreferredSize(new Dimension(1200, 715));
		setMinimumSize(new Dimension(800, 500));
		setLayout(new BorderLayout(10, 0));
		setIconImages(getIcons());
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		canvas = new Canvas();
		
		initComponents();


		canvas.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				newCanvasSize.set(canvas.getSize());
			}
		});

		addWindowFocusListener(new WindowAdapter()
		{
			@Override
			public void windowGainedFocus(WindowEvent e)
			{
				canvas.requestFocusInWindow();
			}
		});

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				if (projectWasModified) {
					int	returnVal = JOptionPane.showConfirmDialog(null, "You have not saved your changes yet, would you like to save now?", "Warning", JOptionPane.YES_NO_CANCEL_OPTION);
					
					if (returnVal == JOptionPane.YES_OPTION) {
						if (ModelCreator.currentProject.filePath == null) {
							SaveProjectAs();
						} else {
							SaveProject(new File(ModelCreator.currentProject.filePath));	
						}
						
					}
					
					if (returnVal == JOptionPane.CANCEL_OPTION) {
						return;
					}

				}
				
				closeRequested = true;
			}
		});

		
		pack();
		setVisible(true);
		setLocationRelativeTo(null);

		initDisplay();

		
		
		currentProject.LoadIntoEditor(getElementManager());
		updateValues();

		
		prevFrameMillisec = System.currentTimeMillis();
		
		try
		{
			Display.create();

			loop();

			Display.destroy();
			dispose();
			System.exit(0);
		}
		catch (Exception e1)
		{
			e1.printStackTrace();
		}
	}
	

	public static void DidModify() {
		projectWasModified = true;
	}

	
	public void initComponents()
	{
		Icons.init(getClass());
		
		manager = new RightTopPanel(this);

		leftKeyframesPanel = new LeftKeyFramesPanel(manager);
		leftKeyframesPanel.setVisible(false);
		add(leftKeyframesPanel, BorderLayout.WEST);

		canvas.setPreferredSize(new Dimension(1000, 850));
		add(canvas, BorderLayout.CENTER);

		canvas.setFocusable(true);
		canvas.setVisible(true);
		canvas.requestFocus();

		modelrenderer = new ModelRenderer(manager);
		
		scroll = new JScrollPane((JPanel) manager);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		add(scroll, BorderLayout.EAST);
		
		uvSidebar = new LeftUVSidebar("UV Editor", manager);
		
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		setJMenuBar(new GuiMain(this));
	}

	private List<Image> getIcons()
	{
		List<Image> icons = new ArrayList<Image>();
		icons.add(Toolkit.getDefaultToolkit().getImage("res/icons/set/icon_16x.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("res/icons/set/icon_32x.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("res/icons/set/icon_64x.png"));
		icons.add(Toolkit.getDefaultToolkit().getImage("res/icons/set/icon_128x.png"));
		return icons;
	}

	
	
	public static void updateValues()
	{
		if (updatingValues) return;
		
		updatingValues = true;
		
		if (currentProject.SelectedAnimation != null) {
			currentProject.SelectedAnimation.calculateAllFrames(currentProject);
		}
		
	 	((RightTopPanel)manager).updateValues();
	 	leftKeyframesPanel.updateValues();
	 	updateFrame();
	 	
	 	String dash = ModelCreator.projectWasModified ? " * " : " - ";
	 	if (currentProject.filePath == null) {
	 		Instance.setTitle("(untitled)" + dash + windowTitle);
		} else {
			Instance.setTitle(new File(currentProject.filePath).getName() + dash + windowTitle);
		}
		
	 	
	 	updatingValues = false;
	}
	
	public static void updateFrame() {
		leftKeyframesPanel.updateFrame();
		((RightTopPanel)manager).updateFrame();
	}


	
	
	
	public void initDisplay()
	{
		try
		{
			Display.setParent(canvas);
			Display.setVSyncEnabled(true);
			Display.setInitialBackground(0.92F, 0.92F, 0.93F);
		}
		catch (LWJGLException e)
		{
			e.printStackTrace();
		}
	}

	private void loop() throws Exception
	{
		modelrenderer.camera = new Camera(60F, (float) Display.getWidth() / (float) Display.getHeight(), 0.3F, 1000F);		

		Dimension newDim;

		while (!Display.isCloseRequested() && !getCloseRequested())
		{
			for (PendingTexture texture : pendingTextures)
			{
				texture.load();
			}
			pendingTextures.clear();

			newDim = newCanvasSize.getAndSet(null);

			if (newDim != null)
			{
				width = newDim.width;
				height = newDim.height;
			}

			int leftSpacing = modelrenderer.renderedLeftSidebar == null ? 0 : getHeight() < 805 ? SIDEBAR_WIDTH * 2 : SIDEBAR_WIDTH;

			glViewport(leftSpacing, 0, width - leftSpacing, height);

			handleInput(leftSpacing);

			modelrenderer.Render(leftSpacing, width, height, getHeight());
			

			Display.update();

			if (screenshot != null)
			{
				if (screenshot.getFile() != null)
					Screenshot.getScreenshot(width, height, screenshot.getCallback(), screenshot.getFile());
				else
					Screenshot.getScreenshot(width, height, screenshot.getCallback());
				screenshot = null;
			}
			
			
			if (currentProject != null && currentProject.SelectedAnimation != null && currentProject.PlayAnimation) {
				currentProject.SelectedAnimation.NextFrame();
				updateFrame();
			}
			
			// Don't run faster than ~30 FPS (1000 / 30 = 33ms)
			long duration = System.currentTimeMillis() - prevFrameMillisec; 
			Thread.sleep(Math.max(33 - duration, 0));
			prevFrameMillisec = System.currentTimeMillis();
		}
	}

	
	public void handleInput(int offset)
	{
		final float cameraMod = Math.abs(modelrenderer.camera.getZ());

		if (Mouse.isButtonDown(0) | Mouse.isButtonDown(1))
		{
			if (!grabbing)
			{
				lastMouseX = Mouse.getX();
				lastMouseY = Mouse.getY();
				grabbing = true;
			}
		}
		else
		{
			grabbing = false;
			grabbed = null;
		}

		if (Mouse.getX() < offset)
		{
			modelrenderer.renderedLeftSidebar.handleInput(getHeight());
		}
		else
		{

			if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL))
			{
				if (grabbed == null)
				{
					if (Mouse.isButtonDown(0) | Mouse.isButtonDown(1))
					{
						int openGlName = getElementGLNameAtPos(Mouse.getX(), Mouse.getY());
						if (openGlName >= 0)
						{
							currentProject.selectElementByOpenGLName(openGlName);
							grabbed = manager.getCurrentElement();
						}
					}
				}
				else
				{
					Element element = grabbed;
					int state = getCameraState(modelrenderer.camera);

					int newMouseX = Mouse.getX();
					int newMouseY = Mouse.getY();

					int xMovement = (int) ((newMouseX - lastMouseX) / 20);
					int yMovement = (int) ((newMouseY - lastMouseY) / 20);

					if (xMovement != 0 | yMovement != 0)
					{
						if (Mouse.isButtonDown(0))
						{
							switch (state)
							{
							case 0:
								element.addStartX(xMovement);
								element.addStartY(yMovement);
								break;
							case 1:
								element.addStartZ(xMovement);
								element.addStartY(yMovement);
								break;
							case 2:
								element.addStartX(-xMovement);
								element.addStartY(yMovement);
								break;
							case 3:
								element.addStartZ(-xMovement);
								element.addStartY(yMovement);
								break;
							case 4:
								element.addStartX(xMovement);
								element.addStartZ(-yMovement);
								break;
							case 5:
								element.addStartX(yMovement);
								element.addStartZ(xMovement);
								break;
							case 6:
								element.addStartX(-xMovement);
								element.addStartZ(yMovement);
								break;
							case 7:
								element.addStartX(-yMovement);
								element.addStartZ(-xMovement);
								break;
							}
						}
						else if (Mouse.isButtonDown(1))
						{
							switch (state)
							{
							case 0:
								element.addHeight(yMovement);
								element.addWidth(xMovement);
								break;
							case 1:
								element.addHeight(yMovement);
								element.addDepth(xMovement);
								break;
							case 2:
								element.addHeight(yMovement);
								element.addWidth(-xMovement);
								break;
							case 3:
								element.addHeight(yMovement);
								element.addDepth(-xMovement);
								break;
							case 4:
								element.addDepth(-yMovement);
								element.addWidth(xMovement);
								break;
							case 5:
								element.addDepth(xMovement);
								element.addWidth(yMovement);
								break;
							case 6:
								element.addDepth(yMovement);
								element.addWidth(-xMovement);
								break;
							case 7:
								element.addDepth(-xMovement);
								element.addWidth(-yMovement);
								break;
							case 8:
								element.addDepth(-yMovement);
								element.addWidth(xMovement);
								break;
							}
						}

						if (xMovement != 0)
							lastMouseX = newMouseX;
						if (yMovement != 0)
							lastMouseY = newMouseY;

						updateValues();
						element.updateUV();
					}
				}
			}
			else
			{
				if (Mouse.isButtonDown(0))
				{
					final float modifier = (cameraMod * 0.05f);
					modelrenderer.camera.addX((float) (Mouse.getDX() * 0.01F) * modifier);
					modelrenderer.camera.addY((float) (Mouse.getDY() * 0.01F) * modifier);
				}
				else if (Mouse.isButtonDown(1))
				{
					final float modifier = applyLimit(cameraMod * 0.1f);
					modelrenderer.camera.rotateX(-(float) (Mouse.getDY() * 0.5F) * modifier);
					final float rxAbs = Math.abs(modelrenderer.camera.getRX());
					modelrenderer.camera.rotateY((rxAbs >= 90 && rxAbs < 270 ? -1 : 1) * (float) (Mouse.getDX() * 0.5F) * modifier);
				}

				final float wheel = Mouse.getDWheel();
				if (wheel != 0)
				{
					modelrenderer.camera.addZ(wheel * (cameraMod / 5000F));
				}
			}
		}
	}

	public int getElementGLNameAtPos(int x, int y)
	{
		IntBuffer selBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder()).asIntBuffer();
		int[] buffer = new int[256];

		IntBuffer viewBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asIntBuffer();
		int[] viewport = new int[4];

		int hits;
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewBuffer);
		viewBuffer.get(viewport);

		GL11.glSelectBuffer(selBuffer);
		GL11.glRenderMode(GL11.GL_SELECT);
		GL11.glInitNames();
		GL11.glPushName(0);
		GL11.glPushMatrix();
		{
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GLU.gluPickMatrix(x, y, 1, 1, IntBuffer.wrap(viewport));
			GLU.gluPerspective(60F, (float) (width) / (float) height, 0.3F, 1000F);

			modelrenderer.drawGridAndElements();
		}
		GL11.glPopMatrix();
		hits = GL11.glRenderMode(GL11.GL_RENDER);

		selBuffer.get(buffer);
		if (hits > 0)
		{
			int name = buffer[3];
			int depth = buffer[1];
			
			for (int i = 1; i < hits; i++)
			{
				if ((buffer[i * 4 + 1] < depth || name == 0) && buffer[i * 4 + 3] != 0)
				{
					name = buffer[i * 4 + 3];
					depth = buffer[i * 4 + 1];
				}
			}

			return name;
		}

		return -1;
	}

	public float applyLimit(float value)
	{
		if (value > 0.4F)
		{
			value = 0.4F;
		}
		else if (value < 0.15F)
		{
			value = 0.15F;
		}
		return value;
	}

	public int getCameraState(Camera camera)
	{
		int cameraRotY = (int) (camera.getRY() >= 0 ? camera.getRY() : 360 + camera.getRY());
		int state = (int) ((cameraRotY * 4.0F / 360.0F) + 0.5D) & 3;

		if (camera.getRX() > 45)
		{
			state += 4;
		}
		if (camera.getRX() < -45)
		{
			state += 8;
		}
		return state;
	}


	public void startScreenshot(PendingScreenshot screenshot)
	{
		this.screenshot = screenshot;
	}

	public void setSidebar(LeftSidebar s)
	{
		modelrenderer.renderedLeftSidebar = s;
	}
	
	
	public static List<IDrawable> getRootElementsForRender() {
		if (leftKeyframesPanel.isVisible()) {
			return currentProject.getCurrentFrameRootElements();
		} else {
			return new ArrayList<IDrawable>(currentProject.rootElements);
		}
	}

	public IElementManager getElementManager()
	{
		return manager;
	}
	
	public void close()
	{
		this.closeRequested = true;
	}

	public boolean getCloseRequested()
	{
		return closeRequested;
	}

	

	
	

	private DropTarget getCustomDropTarget()
	{
		 return new DropTarget() {
			private static final long serialVersionUID = 1L;
			
			@Override
		    public synchronized void drop(DropTargetDropEvent evt) {
				DataFlavor flavor = evt.getCurrentDataFlavors()[0];
		        evt.acceptDrop(evt.getDropAction());
				
		        
		        
				try {
					@SuppressWarnings("rawtypes")
					List data = (List)evt.getTransferable().getTransferData(flavor);
					
					for (Object elem : data) {
						if (elem instanceof File) {
							File file = (File)elem;
							
							if (file.getName().endsWith(".json")) {		
								LoadFile(file.getAbsolutePath());
							}
							
							if (file.getName().endsWith(".png")) {								
								File meta = new File(file.getAbsolutePath() + ".mcmeta");
								
								getElementManager().addPendingTexture(new PendingTexture(file, meta, new ITextureCallback()
								{
									@Override
									public void callback(boolean isnew, String errormessage, String texture)
									{										
										if (errormessage != null)
										{
											JOptionPane error = new JOptionPane();
											error.setMessage(errormessage);
											JDialog dialog = error.createDialog(canvas, "Texture Error");
											dialog.setLocationRelativeTo(null);
											dialog.setModal(false);
											dialog.setVisible(true);
										}
									}
								}));
							}
							
							
							return;
						}
							
					}
					
				} catch (Exception e) {
					System.out.println(e);
				}
		        		        
		        evt.dropComplete(true);
		    }
		 };
	}


	public void LoadFile(String filePath)
	{
		if (filePath == null) {
			setTitle("(untitled) - " + windowTitle);
			currentProject.clear();
			currentProject = new Project(null);
			
		} else {
			prefs.put("filePath", filePath);
			Importer importer = new Importer(filePath);			
			currentProject = importer.loadFromJSON();
			currentProject.LoadIntoEditor(ModelCreator.manager);
			setTitle(new File(currentProject.filePath).getName() + " - " + windowTitle);
		}
		
		projectWasModified = false;
		ModelCreator.updateValues();
		currentProject.tree.jtree.updateUI();
		
	}
	

	public void SaveProject(File file)
	{
		Exporter exporter = new Exporter(ModelCreator.currentProject);
		exporter.export(file);
		
		ModelCreator.currentProject.filePath = file.getAbsolutePath(); 
		ModelCreator.projectWasModified = false;
		ModelCreator.updateValues();
	}
	

	public void SaveProjectAs()
	{
		JFileChooser chooser = new JFileChooser(ModelCreator.prefs.get("filePath", "."));
		chooser.setDialogTitle("Output Directory");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setApproveButtonText("Save");

		FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON (.json)", "json");
		chooser.setFileFilter(filter);

		int returnVal = chooser.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION)
		{
			if (chooser.getSelectedFile().exists())
			{
				returnVal = JOptionPane.showConfirmDialog(null, "A file already exists with that name, are you sure you want to override?", "Warning", JOptionPane.YES_NO_OPTION);
			}
			if (returnVal != JOptionPane.NO_OPTION && returnVal != JOptionPane.CLOSED_OPTION)
			{
				String filePath = chooser.getSelectedFile().getAbsolutePath();
				ModelCreator.prefs.put("filePath", filePath);
				
				if (!filePath.endsWith(".json")) {
					chooser.setSelectedFile(new File(filePath + ".json"));
				}
				SaveProject(chooser.getSelectedFile());
			}
		}
	}
}