using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class AsteriodMovement : MonoBehaviour
{

    // need these to move
    Vector3 velocity;
    Transform myTransform;
    // need these to do bounce
    Vector3 topLeftOfScreen;
    Vector3 bottomRightOfScreen;
    // the player (Rocket) transform, so we can check for collision
    public Transform playerTransform;
    // Start is called before the first frame update
    void Start()
    {
        myTransform = gameObject.transform;
        topLeftOfScreen = Camera.main.ScreenToWorldPoint(new Vector3(0, 0, 0));
        bottomRightOfScreen = Camera.main.ScreenToWorldPoint(new Vector3(Screen.width, Screen.height,
        0));
        print("screen extents " + topLeftOfScreen.ToString() + " " + topLeftOfScreen.ToString());
        // random velocity
        float velocityX = Random.Range(5, 10) / 100f;
        float velocityY = Random.Range(5, 10) / 100f;
        velocity = new Vector3(velocityX, velocityY, 0);
        playerTransform = GameObject.Find("spaceship").transform;
    }
}
void FixedUpdate()
{
    myTransform.position += velocity;
    Vector3 currentPos = myTransform.position;
    if (currentPos.x < topLeftOfScreen.x || currentPos.x > bottomRightOfScreen.x)
    {
        velocity.x *= -1;
    }
    if (currentPos.y < topLeftOfScreen.y || currentPos.y > bottomRightOfScreen.y)
    {
        velocity.y *= -1;
    }
    if (collidesWithPlayer())
    {
        print("collision!");
    }
}
bool collidesWithPlayer()
{
    Vector3 playerPos = playerTransform.position;
    Vector3 thisAsteroidPos = myTransform.position;
    float distance = Vector3.Distance(thisAsteroidPos, playerPos);
    //print("distance " + distance);
    if (distance < 1f) return true;
    return false;
}


