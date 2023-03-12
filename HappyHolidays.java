import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static java.lang.Math.*;

public class HappyHolidays {
    public static void main(String[] args){
        JFrame frame = new JFrame();
        frame.setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(new textDisplay("Happy Holidays!"));
        frame.pack();
        frame.setVisible(true);
    }
}

class textDisplay extends JPanel{
//  I'm using a stack because I only need to be able to add and remove from the end of the lst of letters.
    Stack<Letter> letters = new Stack<>();
    Vector2d mousePos = new Vector2d(1,1);
    LinkedList<Particle> particles = new LinkedList<>();
    int letterScreenCenter;
    int letterCount = 0;
    int maxLetters;
    int scale = 5;
    static Random random = new Random();
    boolean mousePressed= false;
    textDisplay(String defaultText){
//      initializes the letters class.
        Letter.setScale(scale);
//      Gets the maximum number of letters that can fit on the screen
        maxLetters = (int)Toolkit.getDefaultToolkit().getScreenSize().getWidth() / (int)(Letter.characterWidth*scale*1.3);
//      Gets the offset to center the first letter placed on the screen.
        letterScreenCenter = ((int) Toolkit.getDefaultToolkit().getScreenSize().getWidth()/ 2) - (int)(Letter.characterWidth*scale*0.65);
//      makes the preferred size, the maximum that would fit on the screen.
        setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());
//      builds the initial set of letters from the default text
        for (char character: defaultText.toCharArray()) addLetter(character);
//      This is used to keep tabs on if the mouse is up or down, just updating the mousePressed Boolean.
        addMouseListener(new MouseListener() {
            @Override
            public void mousePressed(MouseEvent e) {mousePressed = true;}
            @Override
            public void mouseReleased(MouseEvent e) {mousePressed = false;}
            @Override
            public void mouseClicked(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        });
//      Updates the mouse position when the mouse moves
        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {mousePos = new Vector2d(e.getX(),e.getY());}
            @Override
            public void mouseMoved(MouseEvent e) {mousePos = new Vector2d(e.getX(),e.getY());}
        });
//      Handles typing keys
        addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
//              Removes a letter if there are letters to remove when backspace is pressed.
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && letterCount > 0) removeLetter();
//              If character is one that can be rendered, and that there is still space on the screen to render characters, then add the letter.
                else if (letterCount < maxLetters && e.getKeyChar() > 0x1f && e.getKeyChar() < 0x7f) addLetter(e.getKeyChar());
            }
            @Override
            public void keyTyped(KeyEvent e) {}
            @Override
            public void keyReleased(KeyEvent e) {}
        });
//      allows for the component to receive Key events
        setFocusable(true);
//      Telling it to run at ~30 fps
        new Timer(30, e-> repaint()).start();
    }

    private void addLetter(char character){
//      Shifts each letter over by 1/2 a character width, to keep them centered when the new letter is added.
        letters.forEach(letter-> letter.offset(new Vector2d( Letter.characterWidth * -1.3 * scale/2,0)));
//      Creating a new letter and pushing it to the end of the list.
        letters.push(new Letter(character, new Vector2d(letterScreenCenter + Letter.characterWidth * 1.3 * scale * letterCount/2,200)));
        letterCount ++;
    }
    private void removeLetter(){
//      Shifting the letters back over to keep them centered
        letters.forEach(letter-> letter.offset(new Vector2d( Letter.characterWidth * 1.3 * scale/2,0)));
//      removes the last letter added
        letters.pop();
        letterCount--;
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
//      paints the background a nice blue color
        g.setColor(new Color(0x87,0xce,0xeb));
        g.fillRect(0,0,getWidth(),getHeight());

//      if it has not reached the maximum number of particles, then create a new layer of snow.
        if (particles.size()< 13000) snow();

//      Each letter grabs the number of particles that it needs from the pool of particles
//      usedInLetters is the number of particles that were claimed by the letters
        int usedInLetters = 0;
        for(Letter letter:letters) usedInLetters = letter.grabParticles(particles, usedInLetters);

//      tells particles the particles to avoid the mouse if they are near it.
        if (mousePressed) particles.forEach(e->e.avoid(mousePos));

//      applies gravity to all the particles, and tells all the particles to update their positions and velocities.
        particles.parallelStream().peek(e -> e.acc = e.acc.add(0,100)).forEach(e-> e.update(30));

//      Removes all the particles that are below the bottom of the screen.
        particles.removeAll(particles.stream().skip(usedInLetters).filter(e -> e.pos.y > getHeight()).toList());

//      Draws all the particles
        particles.forEach(e-> e.draw(g));
        System.out.println(particles.size());
    }
    public void snow(){
//      spawns particles along the top of the screen with random x positions.
        random.doubles(10,0, getWidth()).forEach(e->particles.add(new Particle(new Vector2d(e, 0))));
    }
}

class Letter{
//  The hash map gets built when the letters are first made, or when the scale is set
    private static HashMap<Character, ArrayList<Vector2d>> letterTargetPatterns;

//  stores the width of the characters, so they can be used else where in the code.
    public static int characterWidth = -1;

//  Decides what factor to scale the generated letters by
    private static int scale;

//  this needs to be called before making letters to build the array of characters that you can use to draw to the screen with particles.
    public static void setScale(int scale){
        Letter.scale = scale;
        buildCharacterPoints();
    }

    ArrayList<Vector2d> targets;
    Letter(char letter, Vector2d position){
//      Checks if character points have been built, if not, then it builds them right then to allow the creation of letters with those points
        if (letterTargetPatterns == null) buildCharacterPoints();
//      retrieves the corresponding set of points for the character that is it meant to display, and makes a copy of the array.
        this.targets = (ArrayList<Vector2d>) letterTargetPatterns.get(letter).clone();
//      Scales the unscaled version of each letter, and moves it to the position that it was created on.
        this.targets.replaceAll(target -> target.scale(scale).add(position));
    }
    public void offset(Vector2d offset){
//      Moves the letter by the specified amount
        this.targets.replaceAll(target -> target.add(offset));
    }
    public int grabParticles(List<Particle> particles, int alreadyClaimed){
//      alreadyClaimed is an offset into the array of particles showing how many have already been claimed by other objects
        int numberClaimed = alreadyClaimed;
//      if the letter doesn't have any points, then it can't claim any particles to display it
        if (targets == null) return numberClaimed;

        for (Vector2d target : targets) {
//          Checks if there are enough particles to claim another, if not, stop claiming particles
            if (numberClaimed >= particles.size()) break;
//          tells the claimed particles to move towards the positions specified by the character's target
            particles.get(numberClaimed).converge(target);
//          adds to the offset when it claims a point
            numberClaimed++;
        }
//      returns the new number of particles that have been claimed, so any other letters that want to claim points know how many have been claimed.
        return numberClaimed;
    }
    private static void buildCharacterPoints() {
//      This method initializes the hashmap that converts chars to the array of points to draw them.
//      To do this, it uses buffer Images to rasterize the fonts, then checks the pixel values to determine if pixel should have a point associated with it
        letterTargetPatterns = new HashMap<>();
//      Creating a new font render context to get the boundaries of the font to be displayed on the screen
        FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, true);
//      Creates the font to be rendered, scaled to control the density particles when displaying the font at larger sizes
        Font font = new Font(Font.MONOSPACED, Font.BOLD, 4*scale);
//      the for loop goes through all the characters from SPACE to ~ which are 0x20 and 0x7e respectively
        for (int i = 0x1f; i < 0x7f; i++){
//          Gets the boundary of drawing the character, possibly doesn't need to be run every time,
//          but this gets run infrequently it doesn't really matter,
//          and Im not sure if it can vary between characters in a font.
            Rectangle2D bounds = font.getStringBounds(String.valueOf((char)i), fontRenderContext);
//          Creates a buffer image to draw each character on, with the width and height of the boundary of the character
            BufferedImage bi = new BufferedImage((int)bounds.getWidth(),(int)(bounds.getHeight()),BufferedImage.TYPE_INT_RGB);
//          Updates the character width to fit the largest character
            if (bounds.getWidth() > characterWidth) characterWidth = (int)bounds.getWidth();
//          gets the graphics object of the bufferedImage, draws the font, and disposes of the graphics image.
            Graphics g = bi.getGraphics();
            g.setFont(font);
            g.drawString(Character.toString(i), 0,(int)(bounds.getHeight()/2));
            g.dispose();

//          creates the array list
            ArrayList<Vector2d> letterPoints = new ArrayList<>();
//          for each pixel, if the pixel is not empty, add its coordinates to array of the points
            for (int j = 0; j < bi.getWidth(); j++)
                for (int k = 0; k < bi.getHeight(); k++)
                    if (bi.getRGB(j,k) == -1) letterPoints.add(new Vector2d(j,k));

//          adds the array points and character to the letterTargetPatterns hashMap.
            letterTargetPatterns.put((char)i, letterPoints);
        }
    }
}

class Particle{
    Vector2d pos;
    Vector2d vel;
    Vector2d acc;
    double maxSpeed = 2;
    public Particle(Vector2d pos){
        this.pos= pos;
        this.vel = new Vector2d(0,0);
        this.acc = new Vector2d(0,0);
    }
    public void update(double dt){
        pos = pos.add(vel.scale(1/dt));
        vel = vel.add(acc.scale(1/dt)).scale(0.95);
        acc = Vector2d.Zero();
        if (vel.length() > maxSpeed) vel.norm().scale(maxSpeed);
    }
    public void converge(Vector2d target){
        acc = acc.add(target.sub(pos));
    }
    public void avoid(Vector2d target){
        Vector2d diff= pos.sub(target);
        acc = diff.length() < 100? acc.add(diff.scale(100/diff.length())):acc;
    }
    public void draw(Graphics g){
        g.setColor(Color.white);
        g.fillOval((int)pos.x-2, (int)pos.y-2, 4,4);
    }
}

// A class to store an x,y pair and a bunch of math that you can do on them, to make my life easier.
class Vector2d{
    final double x;
    final double y;

    public Vector2d(double x, double y) {
        this.x = x;
        this.y = y;
    }
//   A bunch of vector math
    public Vector2d add(Vector2d v){
        return new Vector2d(this.x + v.x, this.y + v.y);
    }
    public Vector2d add(double x, double y){
        return new Vector2d(this.x + x, this.y + y);
    }
    public Vector2d sub(Vector2d v){
        return new Vector2d(this.x - v.x, this.y - v.y);
    }
    public Vector2d sub(double x, double y){
        return new Vector2d(this.x - x, this.y - y);
    }
    public Vector2d norm(){
        double div = this.length();
        if (div == 0){
            return new Vector2d(0,1);
        }
        return new Vector2d(this.x / div, this.y / div);
    }
    public Vector2d scale(double scalar){
        return new Vector2d(this.x * scalar, this.y * scalar);
    }
    public double length(){
        return sqrt(x*x + y*y);
    }
//  returns a new Vector with an x and y of 0.
    public static Vector2d Zero(){return new Vector2d(0,0);}
}
