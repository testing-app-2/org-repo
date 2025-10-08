using Newtonsoft.Json;
using System;

namespace ProjectA
{
    public class AClass
    {
        public void PrintObject()
        {
            var user = new { Name = "Chinmay", Email = "test@example.com" };
            Console.WriteLine(JsonConvert.SerializeObject(user));
        }
    }
}
