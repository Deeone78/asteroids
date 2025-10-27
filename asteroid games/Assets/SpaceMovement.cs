using System.Collections;
using System.Collections.Generic;
using UnityEngine;


public class SpaceMovement : MonoBehaviour
{
    
    //  Vector2 astriod;
    float speed = 0.01f;
     public GameObject rocket;
    // Start is called before the first frame update
    void Start()
    {
        
        // astriod = new Vector2(0.0f, 0.0f);
        //rocket.Vector2.x = astriod.x;
    }

    // Update is called once per frame
    void Update()
    {
        // astriod.x = astriod.x + 1;
        //rocket.Vector2.x += 1;
        if (Input.GetKeyDown(KeyCode.Space))
        {
            rocket.transform.position = new Vector3(rocket.transform.position.x, rocket.transform.position.y + speed, rocket.transform.position.z);
        }
        if (Input.GetKeyDown(KeyCode.RightArrow))
        {
            rocket.transform.Rotate(0, 0, -10);
        }
        if (Input.GetKeyDown(KeyCode.LeftArrow))
        {
            rocket.transform.Rotate(0, 0, 10);
        }
    }
}

/*respawn = GameObject.FindWithTag("Respawn");

Instantiate(respawnPrefab, respawn.transform.position, respawn.transform.rotation);*/